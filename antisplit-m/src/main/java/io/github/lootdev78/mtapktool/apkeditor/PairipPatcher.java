package io.github.lootdev78.mtapktool.apkeditor;

import com.reandroid.apk.APKLogger;
import com.reandroid.apk.ApkModule;
import com.reandroid.apk.DexFileInputSource;
import com.reandroid.archive.ByteInputSource;
import com.reandroid.arsc.chunk.xml.AndroidManifestBlock;
import com.reandroid.arsc.chunk.xml.ResXmlElement;
import com.reandroid.dex.common.AccessFlag;
import com.reandroid.dex.key.MethodKey;
import com.reandroid.dex.key.StringKey;
import com.reandroid.dex.model.*;
import com.reandroid.dex.ins.Opcode;
import com.reandroid.dex.smali.SmaliReader;
import java.io.*;
import java.util.*;

/** Independent REAndroid implementation of the reference's known Pairip workflow. */
final class PairipPatcher {
    private static final String STARTUP = "Lcom/pairip/StartupLauncher;";
    private static final Set<String> CHECK_CLASSES = new HashSet<>(Arrays.asList(
        "Lcom/pairip/SignatureCheck;", "Lcom/pairip/licensecheck/LicenseClient;",
        "Lcom/pairip/licensecheck2/LicenseClient2;", "Lcom/pairip/licensecheck3/LicenseClientV3;",
        "Lcom/pairip/licensecheck/LicenseActivity;"));
    private PairipPatcher() {}

    static void patch(ApkModule module, ApkEditorEngine.Payloads payloads, APKLogger logger) throws IOException {
        List<DexFile> opened = new ArrayList<>();
        List<DexFileInputSource> inputs = module.listDexFiles();
        Map<String, DexFile> files = new LinkedHashMap<>();
        List<String> junkTypes = new ArrayList<>();
        int changedMethods = 0, maxDex = 0;
        try {
            for (DexFileInputSource input : inputs) {
                cancelled();
                DexFile dex;
                try (InputStream stream = input.openStream()) { dex = DexFile.read(stream); }
                opened.add(dex); files.put(input.getAlias(), dex);
                String name = input.getSimpleName();
                if (name.matches("classes\\d*\\.dex")) {
                    String index = name.substring(7, name.length() - 4);
                    maxDex = Math.max(maxDex, index.isEmpty() ? 1 : Integer.parseInt(index));
                }
                Iterator<DexClass> classes = dex.getDexClasses();
                while (classes.hasNext()) { cancelled(); DexClass cls = classes.next(); if (isJunkClass(cls)) junkTypes.add(cls.getKey().getTypeName()); }
            }
            for (Map.Entry<String, DexFile> file : files.entrySet()) {
                boolean changed = false;
                List<DexClass> classes = new ArrayList<>(); file.getValue().getDexClasses().forEachRemaining(classes::add);
                for (DexClass cls : classes) {
                    cancelled(); String type = cls.getKey().getTypeName();
                    List<DexMethod> methods = new ArrayList<>(); cls.getDirectMethods().forEachRemaining(methods::add); cls.getVirtualMethods().forEachRemaining(methods::add);
                    if (CHECK_CLASSES.contains(type)) {
                        for (DexMethod method : methods) {
                            cancelled(); String name = method.getName();
                            boolean signature = name.equals("verifySignatureMatches"), integrity = name.equals("verifyIntegrity");
                            boolean emptyVoid = !name.equals("<init>") && method.getInstructionsCount() > 0 && method.getKey().getReturnType().getTypeName().equals("V");
                            if (!(signature || integrity || emptyVoid)) continue;
                            stub(method); changed = true; changedMethods++;
                            logger.logMessage("RePairip: " + type + "->" + name);
                        }
                    } else if (STARTUP.equals(type)) {
                        DexMethod helper = cls.getOrCreateStaticMethod(MethodKey.parse(STARTUP + "->pairip()V"));
                        helper.clearCode(); helper.setLocalRegistersCount(1); helper.refreshParameterRegistersCount();
                        for (String junk : junkTypes) {
                            cancelled(); helper.parseInstruction("const-class v0, " + junk);
                            helper.parseInstruction("invoke-static {v0}, Lcom/pairip/PairipLog;->put(Ljava/lang/Class;)V");
                        }
                        helper.parseInstruction("return-void");
                        for (DexMethod method : methods) {
                            if (!method.getName().equals("launch")) continue;
                            for (int i = method.getInstructionsCount() - 1; i >= 0; i--) {
                                DexInstruction instruction = method.getInstruction(i); MethodKey reference = instruction.getKeyAsMethod();
                                if (reference == null || !reference.getDeclaring().getTypeName().equals("Lcom/pairip/VMRunner;") || !reference.getName().equals("invoke")) continue;
                                int index = i + 1;
                                // Keep move-result adjacent to its invoke; REAndroid relocates labels/tries.
                                if (index < method.getInstructionsCount()) {
                                    DexInstruction next = method.getInstruction(index);
                                    if (next.is(Opcode.MOVE_RESULT) || next.is(Opcode.MOVE_RESULT_OBJECT) || next.is(Opcode.MOVE_RESULT_WIDE)) index++;
                                }
                                if (index < method.getInstructionsCount() && MethodKey.parse(STARTUP + "->pairip()V").equals(method.getInstruction(index).getKeyAsMethod())) continue;
                                method.parseInstruction(index, SmaliReader.of("invoke-static {}, " + STARTUP + "->pairip()V"));
                                changedMethods++;
                            }
                        }
                        changed = true;
                    }
                }
                if (changed) { file.getValue().refreshFull(); module.add(new ByteInputSource(file.getValue().getBytes(), file.getKey())); }
            }
            if (changedMethods == 0) throw new IOException("Keine unterstützten Pairip-Signaturprüfungen gefunden");
            if (payloads == null) throw new IOException("RePairip-Payload fehlt");
            DexFile log;
            try (InputStream stream = new ByteArrayInputStream(payloads.read("pairip/log.dex"))) { log = DexFile.read(stream); }
            opened.add(log);
            Set<String> helperTypes = new HashSet<>();
            Iterator<DexClass> helpers = log.getDexClasses();
            while (helpers.hasNext()) {
                DexClass cls = helpers.next(); helperTypes.add(cls.getKey().getTypeName());
                if (cls.getKey().getTypeName().equals("Lcom/pairip/PairipLog;")) {
                    Iterator<DexField> fields = cls.getStaticFields();
                    while (fields.hasNext()) { DexField field = fields.next(); if (field.getName().equals("DIR_PATH")) field.setStaticValue(StringKey.create("/data/data/" + module.getPackageName() + "/dictionary")); }
                }
            }
            // Remove earlier helper definitions to avoid duplicate types on repeated processing.
            for (Map.Entry<String, DexFile> file : files.entrySet()) {
                List<DexClass> duplicates = new ArrayList<>();
                file.getValue().getDexClasses().forEachRemaining(cls -> { if (helperTypes.contains(cls.getKey().getTypeName())) duplicates.add(cls); });
                if (!duplicates.isEmpty()) {
                    duplicates.forEach(DexClass::removeSelf); file.getValue().refreshFull();
                    module.add(new ByteInputSource(file.getValue().getBytes(), file.getKey()));
                }
            }
            log.refreshFull(); module.add(new ByteInputSource(log.getBytes(), "classes" + (maxDex + 1) + ".dex"));
            patchManifest(module.getAndroidManifest());
            logger.logMessage("RePairip: " + changedMethods + " Prüfmethoden/Startaufrufe angepasst, " + junkTypes.size() + " Hilfsklassen erfasst");
            cancelled();
        } finally { for (DexFile dex : opened) dex.close(); }
    }

    private static boolean isJunkClass(DexClass cls) {
        if (cls.getDirectMethodsCount() + cls.getVirtualMethodsCount() != 0 || cls.getAccessFlagsValue() != 1) return false;
        if (cls.getSuperClassKey() == null || !cls.getSuperClassKey().getTypeName().equals("Ljava/lang/Object;")) return false;
        List<DexField> fields = new ArrayList<>(); cls.getStaticFields().forEachRemaining(fields::add); cls.getInstanceFields().forEachRemaining(fields::add);
        if (fields.isEmpty()) return false;
        for (DexField field : fields) {
            String type = field.getKey().getType().getTypeName();
            if (field.getAccessFlagsValue() != 9 || field.getStaticValue() != null || !(type.equals("Ljava/lang/String;") || type.equals("Ljava/lang/reflect/Method;"))) return false;
        }
        return true;
    }
    private static void stub(DexMethod method) throws IOException {
        String type = method.getKey().getReturnType().getTypeName();
        method.removeAccessFlag(AccessFlag.ABSTRACT); method.removeAccessFlag(AccessFlag.NATIVE);
        method.clearCode(); method.setLocalRegistersCount(type.equals("V") ? 0 : type.equals("J") || type.equals("D") ? 2 : 1); method.refreshParameterRegistersCount();
        if (type.equals("V")) method.parseInstruction("return-void");
        else if (type.startsWith("L") || type.startsWith("[")) { method.parseInstruction("const/4 v0, 0x0"); method.parseInstruction("return-object v0"); }
        else if (type.equals("J") || type.equals("D")) {
            method.parseInstruction(type.equals("D") ? "const-wide v0, 0x3ff0000000000000" : "const-wide/16 v0, 0x1"); method.parseInstruction("return-wide v0");
        } else { method.parseInstruction(type.equals("F") ? "const v0, 0x3f800000" : "const/4 v0, 0x1"); method.parseInstruction("return v0"); }
    }
    private static void patchManifest(AndroidManifestBlock manifest) {
        if (manifest == null) return;
        manifest.setExtractNativeLibs(true);
        ResXmlElement root = manifest.getManifestElement();
        for (String name : new String[]{"requiredSplitTypes", "splitTypes", "isSplitRequired", "isFeatureSplit", "split"}) {
            root.removeAttributeIf(attribute -> name.equals(attribute.getName()));
        }
        Set<String> metadata = new HashSet<>(Arrays.asList("com.android.stamp.source", "com.android.stamp.type", "com.android.vending.splits", "com.android.vending.derived.apk.id", "com.android.dynamic.apk.fused.modules", "com.android.vending.splits.required"));
        List<ResXmlElement> remove = new ArrayList<>();
        Iterator<ResXmlElement> elements = manifest.recursiveElements();
        while (elements.hasNext()) {
            ResXmlElement element = elements.next(); String tag = element.getName(), name = AndroidManifestBlock.getAndroidNameValue(element);
            if ((tag.equals("meta-data") && metadata.contains(name)) || (tag.equals("activity") && "com.pairip.licensecheck.LicenseActivity".equals(name)) ||
                (tag.equals("provider") && "com.pairip.licensecheck.LicenseContentProvider".equals(name)) || (tag.equals("uses-permission") && "com.android.vending.CHECK_LICENSE".equals(name))) remove.add(element);
        }
        remove.forEach(ResXmlElement::removeSelf); manifest.refreshFull();
    }
    private static void cancelled() throws InterruptedIOException {
        if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException("RePairip-Auftrag abgebrochen");
    }
}
