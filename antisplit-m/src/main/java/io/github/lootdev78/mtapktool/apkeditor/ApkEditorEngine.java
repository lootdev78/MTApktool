package io.github.lootdev78.mtapktool.apkeditor;

import com.reandroid.apk.APKLogger;
import com.reandroid.apk.ApkModule;
import com.reandroid.apk.DexFileInputSource;
import com.reandroid.apkeditor.Util;
import com.reandroid.apkeditor.protect.Protector;
import com.reandroid.apkeditor.protect.ProtectorOptions;
import com.reandroid.apkeditor.refactor.AutoRefactor;
import com.reandroid.apkeditor.refactor.PublicXmlRefactor;
import com.reandroid.apkeditor.refactor.TypeNameRefactor;
import com.reandroid.archive.ByteInputSource;
import com.reandroid.archive.FileInputSource;
import com.reandroid.archive.InputSource;
import com.reandroid.arsc.chunk.xml.AndroidManifestBlock;
import com.reandroid.dex.model.DexClass;
import com.reandroid.dex.model.DexFile;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/** Independently implemented MT workflows using the existing REAndroid library. */
public final class ApkEditorEngine {
    private ApkEditorEngine() {}
    public enum Action { KILL_SIGNATURE, REFACTOR, OPTIMIZE, PROTECT }
    public static final class Options {
        public Action action;
        public String killMethod = "MT";
        public File publicXml;
        public boolean fixTypeNames = true;
        public boolean cleanMeta = true;
        public boolean skipManifest;
        public boolean confuseZip;
        public int dexLevel;
        public boolean deepOptimize;
        public boolean preserveDebug = true;
        public int maxPasses = 5;
        public final List<String> deletePatterns = new ArrayList<>();
    }
    public interface Payloads { byte[] read(String path) throws IOException; }

    public static void execute(File input, File output, Options options, APKLogger logger,
                               Payloads payloads, String originalCertificate) throws IOException {
        if (options == null || options.action == null) throw new IOException("Keine APK-Aktion gewählt");
        if (!input.isFile()) throw new IOException("APK nicht gefunden: " + input);
        if (input.getCanonicalFile().equals(output.getCanonicalFile())) throw new IOException("Eingabe und Ausgabe müssen verschieden sein");
        if (output.exists()) throw new IOException("Ausgabedatei existiert bereits: " + output);
        checkCancelled();
        try {
            if (options.action == Action.OPTIMIZE && !options.deepOptimize) {
                optimizeZip(input, output, options, logger);
            } else {
                ApkModule module = ApkModule.loadApkFile(logger, input);
                try {
                    module.setLoadDefaultFramework(false);
                    switch (options.action) {
                        case KILL_SIGNATURE:
                            if ("RePairip".equals(options.killMethod)) PairipPatcher.patch(module, payloads, logger);
                            else injectSignaturePayload(module, input, payloads, originalCertificate, logger);
                            stripSignatures(module); module.writeApk(output); break;
                        case REFACTOR:
                            refactor(module, options, logger); stripSignatures(module);
                            if (options.cleanMeta) module.getZipEntryMap().removeIf(Pattern.compile("(?i)^META-INF/.*"));
                            module.writeApk(output); break;
                        case PROTECT:
                            String protectedBy = protection(module);
                            if (protectedBy != null) throw new IOException("APK bereits geschützt: " + protectedBy);
                            stripSignatures(module);
                            ProtectorOptions protect = new ProtectorOptions();
                            protect.skipManifest = options.skipManifest; protect.confuse_zip = options.confuseZip;
                            protect.dexLevel = options.dexLevel;
                            module.add(new ByteInputSource("engine=REAndroid-APKEditor\ncreator=MTAPKTool\n".getBytes(java.nio.charset.StandardCharsets.UTF_8), "assets/MTAPKTool/protection.properties"));
                            new Protector(protect, logger, module).write(output); break;
                        case OPTIMIZE:
                            optimizeDex(module, options, logger); stripSignatures(module);
                            File intermediate = File.createTempFile("mt-dex-opt-", ".apk", output.getAbsoluteFile().getParentFile());
                            try { module.writeApk(intermediate); optimizeZip(intermediate, output, options, logger); }
                            finally { Files.deleteIfExists(intermediate.toPath()); }
                            break;
                    }
                    checkCancelled();
                } finally { module.close(); }
            }
            validateApk(output, options.action == Action.PROTECT && options.confuseZip);
            checkCancelled();
        } catch (IOException | RuntimeException error) {
            try { Files.deleteIfExists(output.toPath()); } catch (IOException cleanup) { error.addSuppressed(cleanup); }
            throw error;
        }
    }

    private static void refactor(ApkModule module, Options options, APKLogger logger) throws IOException {
        if (!module.hasTableBlock()) throw new IOException("Die APK enthält keine Ressourcen-Tabelle");
        String protectedBy = protection(module);
        if (protectedBy != null) throw new IOException("APK geschützt: " + protectedBy);
        if (options.fixTypeNames) {
            TypeNameRefactor types = new TypeNameRefactor(module); types.setApkLogger(logger); types.refactor();
        }
        checkCancelled();
        if (options.publicXml != null) {
            if (!options.publicXml.isFile()) throw new IOException("public.xml nicht gefunden");
            new PublicXmlRefactor(module, options.publicXml).refactor();
        } else new AutoRefactor(module).refactor();
        logger.logMessage("Ressourcen-Namen und Pfade überarbeitet");
        String message = module.refreshTable();
        if (message != null) logger.logMessage(message);
    }

    private static void optimizeDex(ApkModule module, Options options, APKLogger logger) throws IOException {
        for (DexFileInputSource source : module.listDexFiles()) {
            checkCancelled();
            try (InputStream stream = source.openStream()) {
                DexFile dex = DexFile.read(stream);
                try {
                    if (!options.preserveDebug) {
                        Iterator<DexClass> classes = dex.getDexClasses();
                        while (classes.hasNext()) { checkCancelled(); classes.next().clearDebug(); }
                    }
                    int removed = 0;
                    for (int pass = 0; pass < Math.max(1, Math.min(options.maxPasses, 25)); pass++) {
                        checkCancelled();
                        int changed = dex.clearDuplicateData() + dex.shrink(); removed += changed;
                        if (changed == 0) break;
                    }
                    dex.refreshFull();
                    module.add(new ByteInputSource(dex.getBytes(), source.getAlias()));
                    logger.logMessage(source.getAlias() + ": " + removed + " unbenutzte/duplizierte Datensätze entfernt");
                } finally { dex.close(); }
            }
        }
    }

    private static void optimizeZip(File input, File output, Options options, APKLogger logger) throws IOException {
        List<Pattern> removals = new ArrayList<>();
        for (String pattern : options.deletePatterns) {
            if (!pattern.trim().isEmpty()) removals.add(Pattern.compile(pattern.trim()));
        }
        Set<String> names = new HashSet<>();
        try (ZipFile zip = new ZipFile(input); ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(output.toPath()))) {
            out.setLevel(9);
            Iterator<? extends ZipEntry> entries = zip.entries().asIterator();
            byte[] buffer = new byte[65536];
            while (entries.hasNext()) {
                checkCancelled(); ZipEntry entry = entries.next(); String name = entry.getName();
                if (!names.add(name)) throw new IOException("Doppelter APK-Eintrag: " + name);
                if (isSignature(name) || entry.isDirectory()) continue;
                boolean essential = name.equals("AndroidManifest.xml") || name.equals("resources.arsc") || name.matches("classes\\d*\\.dex") || name.startsWith("lib/");
                if (!essential && removals.stream().anyMatch(p -> p.matcher(name).matches())) {
                    logger.logMessage("Entfernen: " + name); continue;
                }
                ZipEntry next = new ZipEntry(name); next.setTime(entry.getTime());
                boolean stored = name.equals("AndroidManifest.xml") || name.equals("resources.arsc") || name.endsWith(".so") || (name.startsWith("res/") && !name.endsWith(".xml"));
                if (stored) { next.setMethod(ZipEntry.STORED); next.setSize(entry.getSize()); next.setCrc(entry.getCrc()); }
                else next.setMethod(ZipEntry.DEFLATED);
                out.putNextEntry(next);
                try (InputStream stream = zip.getInputStream(entry)) {
                    int count; while ((count = stream.read(buffer)) >= 0) { checkCancelled(); if (count > 0) out.write(buffer, 0, count); }
                }
                out.closeEntry(); logger.logVerbose("Packen: " + name);
            }
        }
        logger.logMessage("APK optimiert: " + input.length() + " → " + output.length() + " Bytes");
    }

    private static void injectSignaturePayload(ApkModule module, File input, Payloads payloads,
                                               String certificate, APKLogger logger) throws IOException {
        if (payloads == null || certificate == null || certificate.isEmpty()) throw new IOException("Originalsignatur oder SignatureKiller-Modul fehlt");
        AndroidManifestBlock manifest = module.getAndroidManifest();
        if (manifest == null) throw new IOException("AndroidManifest.xml fehlt");
        String killer = "bin.mt.signature.KillerApplication";
        String application = manifest.getApplicationClassName();
        if (killer.equals(application)) throw new IOException("MT-Signatur-Hook ist bereits vorhanden");
        Properties config = new Properties();
        config.setProperty("package", module.getPackageName()); config.setProperty("signature", certificate);
        if (application != null && !application.isEmpty()) {
            if (application.startsWith(".")) application = module.getPackageName() + application;
            else if (application.indexOf('.') < 0) application = module.getPackageName() + "." + application;
            config.setProperty("appClass", application);
        }
        int maxDex = 0;
        for (DexFileInputSource dex : module.listDexFiles()) {
            String name = dex.getSimpleName();
            if (name.matches("classes\\d*\\.dex")) {
                String number = name.substring(7, name.length() - 4);
                maxDex = Math.max(maxDex, number.isEmpty() ? 1 : Integer.parseInt(number));
            }
        }
        module.add(new ByteInputSource(payloads.read("signature_killer/killer.dex"), maxDex == 0 ? "classes.dex" : "classes" + (maxDex + 1) + ".dex"));
        for (String abi : new String[]{"arm64-v8a", "armeabi-v7a", "x86_64", "x86"}) {
            String name = "lib/" + abi + "/libSignatureKiller.so";
            InputSource library = new ByteInputSource(payloads.read("signature_killer/" + name), name); library.setMethod(0); module.add(library);
        }
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(); config.store(bytes, "Generated by MTAPKTool");
        module.add(new ByteInputSource(bytes.toByteArray(), "assets/SignatureKiller/config.properties"));
        module.add(new FileInputSource(input, "assets/SignatureKiller/origin.apk"));
        manifest.setApplicationClassName(killer); manifest.setExtractNativeLibs(true); manifest.refreshFull();
        logger.logMessage("MT-Hook und Originalzertifikat eingefügt; ursprüngliche Application bleibt in der Konfiguration");
    }

    public static boolean isSignature(String name) {
        String upper = name.toUpperCase(Locale.ROOT);
        return upper.equals("STAMP-CERT-SHA256") || (upper.startsWith("META-INF/") &&
                (upper.endsWith(".RSA") || upper.endsWith(".DSA") || upper.endsWith(".EC") || upper.endsWith(".SF") || upper.equals("META-INF/MANIFEST.MF")));
    }
    public static String protection(ApkModule module) {
        if (module.getInputSource("assets/MTAPKTool/protection.properties") != null) return "REAndroid APKEditor (MTAPKTool)";
        return Util.isProtected(module);
    }
    private static void stripSignatures(ApkModule module) {
        module.setApkSignatureBlock(null);
        module.getZipEntryMap().removeIf(source -> isSignature(source.getAlias()));
    }
    public static void validateApk(File apk, boolean protectedZip) throws IOException {
        if (!apk.isFile() || apk.length() == 0) throw new IOException("Keine APK erzeugt");
        // A protected ZIP intentionally sets encryption flags. REAndroid reads its headers without decrypting.
        if (protectedZip) {
            ApkModule module = ApkModule.loadApkFile(apk);
            try { if (!module.hasAndroidManifest()) throw new IOException("AndroidManifest.xml fehlt"); }
            finally { module.close(); }
        } else try (ZipFile zip = new ZipFile(apk)) {
            if (zip.getEntry("AndroidManifest.xml") == null) throw new IOException("AndroidManifest.xml fehlt");
            byte[] buffer = new byte[65536];
            Iterator<? extends ZipEntry> entries = zip.entries().asIterator();
            while (entries.hasNext()) {
                checkCancelled(); ZipEntry entry = entries.next(); if (entry.isDirectory()) continue;
                java.util.zip.CRC32 crc = new java.util.zip.CRC32(); long size = 0;
                try (InputStream stream = zip.getInputStream(entry)) {
                    int count; while ((count = stream.read(buffer)) >= 0) { checkCancelled(); if (count > 0) { crc.update(buffer, 0, count); size += count; } }
                }
                if (entry.getSize() != size || entry.getCrc() != crc.getValue()) throw new IOException("Beschädigter APK-Eintrag: " + entry.getName());
            }
        }
    }
    private static void checkCancelled() throws InterruptedIOException {
        if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException("APK-Auftrag abgebrochen");
    }
}
