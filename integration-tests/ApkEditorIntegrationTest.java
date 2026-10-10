import com.reandroid.apk.APKLogger;
import com.reandroid.apk.ApkModule;
import com.reandroid.apkeditor.Util;
import io.github.lootdev78.mtapktool.apkeditor.ApkEditorEngine;
import java.io.File;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Properties;
import java.util.concurrent.*;
import java.util.zip.ZipFile;
import com.reandroid.dex.model.DexFile;
import com.reandroid.dex.model.DexClass;
import com.reandroid.dex.model.DexMethod;
import com.reandroid.dex.key.TypeKey;
import com.reandroid.dex.key.MethodKey;
import com.reandroid.dex.key.FieldKey;
import com.reandroid.dex.common.AccessFlag;
import com.reandroid.archive.ByteInputSource;
import com.reandroid.arsc.model.ResourceEntry;

public final class ApkEditorIntegrationTest {
    private static int assertions;
    private static final APKLogger LOG = new APKLogger() {
        public void logMessage(String text) { if (text.startsWith("RePairip")) System.out.println(text); }
        public void logVerbose(String text) {}
        public void logError(String text, Throwable error) { throw new AssertionError(text, error); }
    };
    private static void check(boolean condition, String message) {
        assertions++; if (!condition) throw new AssertionError(message);
    }
    private static void run(File input, File output, ApkEditorEngine.Options options, File assets) throws Exception {
        ApkEditorEngine.execute(input, output, options, LOG, p -> Files.readAllBytes(new File(assets, p).toPath()), "test-certificate");
    }
    public static void main(String[] args) throws Exception {
        File input = new File(args[0]), assets = new File(args[1]), work = new File(args[2]);
        byte[] original = Files.readAllBytes(input.toPath());
        ApkEditorEngine.Options optimize = new ApkEditorEngine.Options(); optimize.action = ApkEditorEngine.Action.OPTIMIZE;
        File optimized = new File(work, "optimized.apk"); run(input, optimized, optimize, assets);
        check(optimized.isFile(), "Optimize result missing");
        try (ZipFile zip = new ZipFile(optimized)) {
            check(zip.getEntry("AndroidManifest.xml").getMethod() == 0, "Manifest should be stored");
            check(zip.getEntry("resources.arsc").getMethod() == 0, "Resource table should be stored");
            check(zip.stream().noneMatch(e -> ApkEditorEngine.isSignature(e.getName())), "Old signatures remain");
        }
        ApkEditorEngine.Options refactor = new ApkEditorEngine.Options(); refactor.action = ApkEditorEngine.Action.REFACTOR;
        File refactored = new File(work, "refactored.apk"); run(input, refactored, refactor, assets);
        ApkModule module = ApkModule.loadApkFile(refactored);
        try { check(module.hasTableBlock(), "Refactor lost resources"); check(module.getPackageName() != null, "Refactor lost package"); }
        finally { module.close(); }

        ApkEditorEngine.Options protect = new ApkEditorEngine.Options(); protect.action = ApkEditorEngine.Action.PROTECT;
        protect.skipManifest = true;
        File protectedApk = new File(work, "protected.apk"); run(input, protectedApk, protect, assets);
        module = ApkModule.loadApkFile(protectedApk);
        try { check(ApkEditorEngine.protection(module) != null, "REAndroid protection marker absent"); check(module.hasAndroidManifest(), "Protect lost manifest"); }
        finally { module.close(); }
        File protectedAgain = new File(work, "protected-again.apk");
        try { run(protectedApk, protectedAgain, protect, assets); throw new AssertionError("Already protected APK accepted"); }
        catch (java.io.IOException expected) { check(!protectedAgain.exists(), "Failed result leaked"); }

        protect.confuseZip = true;
        File confused = new File(work, "confused.apk"); run(input, confused, protect, assets);
        ApkEditorEngine.validateApk(confused, true); check(confused.isFile(), "Confused output missing");
        protect.confuseZip = false; protect.skipManifest = false;
        File manifestProtected = new File(work, "manifest-protected.apk"); run(input, manifestProtected, protect, assets);
        check(manifestProtected.isFile(), "Manifest protection output missing");
        protect.skipManifest = true;

        ApkEditorEngine.Options kill = new ApkEditorEngine.Options(); kill.action = ApkEditorEngine.Action.KILL_SIGNATURE;
        File killed = new File(work, "killed.apk"); run(input, killed, kill, assets);
        module = ApkModule.loadApkFile(killed);
        try {
            check("bin.mt.signature.KillerApplication".equals(module.getAndroidManifest().getApplicationClassName()), "Signature application missing");
            check(module.listDexFiles().size() > 0, "Payload dex missing");
        } finally { module.close(); }
        try (ZipFile zip = new ZipFile(killed)) {
            Properties props = new Properties(); props.load(zip.getInputStream(zip.getEntry("assets/SignatureKiller/config.properties")));
            check("test-certificate".equals(props.getProperty("signature")), "Original signature config missing");
            check(zip.getEntry("assets/SignatureKiller/origin.apk").getSize() == input.length(), "Original APK missing");
            for (String abi : new String[]{"arm64-v8a", "armeabi-v7a", "x86", "x86_64"})
                check(zip.getEntry("lib/" + abi + "/libSignatureKiller.so") != null, "Payload ABI missing: " + abi);
        }
        kill.killMethod = "RePairip";
        File missingPairip = new File(work, "no-pairip.apk");
        try { run(input, missingPairip, kill, assets); throw new AssertionError("Unmatched Pairip should fail"); }
        catch (java.io.IOException expected) { check(!missingPairip.exists(), "Unmatched Pairip leaked output"); }

        File dexApk = new File(work, "dex-fixture.apk");
        module = ApkModule.loadApkFile(input);
        int stringId = 0; String stringValue = null;
        try {
            java.util.Iterator<ResourceEntry> resources = module.getTableBlock().getResources();
            while (resources.hasNext()) {
                ResourceEntry entry = resources.next();
                if (!"string".equals(entry.getType())) continue;
                for (com.reandroid.arsc.value.Entry variant : entry) {
                    com.reandroid.arsc.value.ResValue value = variant.getResValue();
                    if (variant.getResConfig().isDefault() && value != null && value.getValueType() == com.reandroid.arsc.value.ValueType.STRING) {
                        value.setValueAsString("Hello editor workflow");
                        stringId = entry.getResourceId(); stringValue = value.getValueAsString(); break;
                    }
                }
                if (stringId != 0) { entry.setName("***"); break; }
            }
            check(stringId != 0, "String fixture missing");
            DexFile dex = DexFile.createDefault();
            try {
                DexClass cls = dex.getOrCreateFirst().getOrCreateClass(TypeKey.parse("Lcom/pairip/SignatureCheck;"));
                cls.setSuperClass(TypeKey.parse("Ljava/lang/Object;"));
                DexMethod method = cls.getOrCreateStaticMethod(MethodKey.parse("Lcom/pairip/SignatureCheck;->verifySignatureMatches()Z"));
                method.setLocalRegistersCount(1); method.refreshParameterRegistersCount();
                method.parseInstruction("const/4 v0, 0x0"); method.parseInstruction("return v0");
                DexClass startup = dex.getOrCreateFirst().getOrCreateClass(TypeKey.parse("Lcom/pairip/StartupLauncher;"));
                startup.setSuperClass(TypeKey.parse("Ljava/lang/Object;"));
                DexMethod launch = startup.getOrCreateStaticMethod(MethodKey.parse("Lcom/pairip/StartupLauncher;->launch()V"));
                launch.setLocalRegistersCount(1); launch.refreshParameterRegistersCount();
                launch.parseInstruction("const-string v0, \"check\"");
                launch.parseInstruction("invoke-static {v0}, Lcom/pairip/VMRunner;->invoke(Ljava/lang/String;)Ljava/lang/Object;");
                launch.parseInstruction("move-result-object v0"); launch.parseInstruction("return-void");
                DexClass junk = dex.getOrCreateFirst().getOrCreateClass(TypeKey.parse("Lfixture/Junk;"));
                junk.addAccessFlag(AccessFlag.PUBLIC); junk.setSuperClass(TypeKey.parse("Ljava/lang/Object;"));
                com.reandroid.dex.model.DexField field = junk.getOrCreateStaticField(FieldKey.parse("Lfixture/Junk;->value:Ljava/lang/String;"));
                field.addAccessFlag(AccessFlag.PUBLIC); field.addAccessFlag(AccessFlag.STATIC);
                dex.refreshFull(); module.add(new ByteInputSource(dex.getBytes(), "classes.dex")); module.writeApk(dexApk);
            } finally { dex.close(); }
        } finally { module.close(); }
        File pairip = new File(work, "pairip.apk"); run(dexApk, pairip, kill, assets);
        module = ApkModule.loadApkFile(pairip);
        try (java.io.InputStream stream = module.getInputSource("classes.dex").openStream()) {
            DexFile dex = DexFile.read(stream);
            try {
                DexClass cls = dex.getOrCreateFirst().getOrCreateClass(TypeKey.parse("Lcom/pairip/SignatureCheck;"));
                DexMethod method = cls.getDeclaredMethod(MethodKey.parse("Lcom/pairip/SignatureCheck;->verifySignatureMatches()Z"));
                check(method.getInstructionsCount() == 2, "Pairip check did not get return stub");
                check(method.getInstruction(0).getIns().toString().contains("0x1"), "Pairip check does not return true");
                cls = dex.getOrCreateFirst().getOrCreateClass(TypeKey.parse("Lcom/pairip/StartupLauncher;"));
                method = cls.getDeclaredMethod(MethodKey.parse("Lcom/pairip/StartupLauncher;->launch()V"));
                check(method.getInstruction(2).is(com.reandroid.dex.ins.Opcode.MOVE_RESULT_OBJECT), "move-result no longer adjacent to invoke");
                check(method.getInstruction(3).getKeyAsMethod().getName().equals("pairip"), "Startup helper was not inserted");
                check(cls.getDeclaredMethod(MethodKey.parse("Lcom/pairip/StartupLauncher;->pairip()V")).getInstructionsCount() == 3, "Junk-class helper not generated");
            } finally { dex.close(); }
        } finally { module.close(); }
        module = ApkModule.loadApkFile(pairip);
        try { check(module.listDexFiles().size() == 2, "Pairip logger DEX missing"); }
        finally { module.close(); }
        File named = new File(work, "named.apk"); run(dexApk, named, refactor, assets);
        module = ApkModule.loadApkFile(named);
        try { check(!module.getTableBlock().getResource(stringId).getName().contains("*"), "Obfuscated name remains");
              check(!module.getTableBlock().getResource(stringId).getName().contains("_0x"), "Readable string name not generated"); }
        finally { module.close(); }
        File publicXml = new File(work, "public.xml");
        Files.writeString(publicXml.toPath(), "<resources><public type=\"string\" name=\"from_public_xml\" id=\"0x" + Integer.toHexString(stringId) + "\"/></resources>");
        refactor.publicXml = publicXml;
        File publicResult = new File(work, "public-refactored.apk"); run(dexApk, publicResult, refactor, assets);
        module = ApkModule.loadApkFile(publicResult);
        try { check("from_public_xml".equals(module.getTableBlock().getResource(stringId).getName()), "public.xml mapping not applied"); }
        finally { module.close(); }
        refactor.publicXml = null;
        optimize.deepOptimize = true;
        File deep = new File(work, "deep.apk"); run(dexApk, deep, optimize, assets);
        module = ApkModule.loadApkFile(deep);
        try { check(module.listDexFiles().size() == 1, "Deep optimizer lost dex"); }
        finally { module.close(); }
        optimize.deepOptimize = false;
        protect.confuseZip = false; protect.dexLevel = 1;
        File dexProtected = new File(work, "dex-protected.apk"); run(dexApk, dexProtected, protect, assets);
        check(dexProtected.isFile(), "DEX protection output missing");

        File existing = new File(work, "existing.apk"); byte[] sentinel = {1,2,3}; Files.write(existing.toPath(), sentinel);
        try { run(input, existing, optimize, assets); throw new AssertionError("Existing output accepted"); }
        catch (java.io.IOException expected) { check(Arrays.equals(sentinel, Files.readAllBytes(existing.toPath())), "Existing output was changed"); }
        try { run(input, input, optimize, assets); throw new AssertionError("Input overwrite accepted"); }
        catch (java.io.IOException expected) { check(Arrays.equals(original, Files.readAllBytes(input.toPath())), "Input changed"); }

        File cancelled = new File(work, "cancelled.apk"); Thread.currentThread().interrupt();
        try { run(input, cancelled, optimize, assets); throw new AssertionError("Cancellation ignored"); }
        catch (java.io.InterruptedIOException expected) { check(!cancelled.exists(), "Cancelled result leaked"); }
        finally { Thread.interrupted(); }
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<?> a = pool.submit(() -> { try { run(input, new File(work, "parallel-opt.apk"), optimize, assets); } catch (Exception error) { throw new RuntimeException(error); } });
            Future<?> b = pool.submit(() -> { try { run(input, new File(work, "parallel-refactor.apk"), refactor, assets); } catch (Exception error) { throw new RuntimeException(error); } });
            a.get(120, TimeUnit.SECONDS); b.get(120, TimeUnit.SECONDS);
            check(true, "Parallel workflows completed");
        } finally { pool.shutdownNow(); }
        check(Arrays.equals(original, Files.readAllBytes(input.toPath())), "Original changed after operations");
        System.out.println("PASS APKEditor integration: " + assertions + " assertions");
    }
}
