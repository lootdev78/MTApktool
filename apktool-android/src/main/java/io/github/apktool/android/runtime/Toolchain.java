package io.github.apktool.android.runtime;

import android.content.Context;
import android.content.res.AssetManager;
import android.os.Build;
import android.os.Environment;
import android.system.Os;
import android.system.OsConstants;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import brut.androlib.Config;

/** Android runtime paths and provisioning for the bundled Apktool toolchain. */
public final class Toolchain {
    private static final Object PROVISION_LOCK = new Object();
    public static final String VERSION = "3.1.0-android-final";
    public static final String[] FRAMEWORK_TAGS = {"sdk33", "sdk34", "sdk35", "sdk36"};

    private final Context context;
    private final File root;
    private final File binDir;
    private final File aaptDir;
    private final File frameworkDir;
    private final File inputDir;
    private final File projectsDir;
    private final File outputDir;
    private final File logsDir;

    public Toolchain(Context context) {
        this.context = context.getApplicationContext();
        this.root = new File(Environment.getExternalStorageDirectory(), "apktool");
        this.binDir = new File(root, "bin");
        this.aaptDir = new File(binDir, "aapt2/arm64-v8a");
        this.frameworkDir = new File(root, "frameworks");
        this.inputDir = new File(root, "input");
        this.projectsDir = new File(root, "projects");
        this.outputDir = new File(root, "output");
        this.logsDir = new File(root, "logs");
        configureAndroidRuntimeProperties();
    }

    public File getRoot() { return root; }
    public File getBinDir() { return binDir; }
    public File getFrameworkDir() { return frameworkDir; }
    public File getInputDir() { return inputDir; }
    public File getProjectsDir() { return projectsDir; }
    public File getOutputDir() { return outputDir; }
    public File getLogsDir() { return logsDir; }

    public long getRuntimePageSize() {
        try {
            long value = Os.sysconf(OsConstants._SC_PAGESIZE);
            return value > 0 ? value : 4096L;
        } catch (Throwable ignored) {
            return 4096L;
        }
    }

    public boolean isLargePageDevice() {
        return getRuntimePageSize() >= 16 * 1024L;
    }

    public void provision() throws IOException {
        synchronized (PROVISION_LOCK) {
            ensureDirectories();
            requireArm64Payload();
            configureAndroidRuntimeProperties();

            // Assets remain readable as sdk-XX.apk. Runtime copies use Apktool's
            // ordinary framework tag convention so original CLI `-t sdkXX` works.
            // Bundled framework names are owned by the app and refreshed on update;
            // separately installed IDs/tags are left untouched.
            for (int api : new int[]{33, 34, 35, 36}) {
                String asset = "apktool/frameworks/sdk-" + api + ".apk";
                copyAssetIfDifferent(asset, new File(frameworkDir, "1-sdk" + api + ".apk"));
                if (api == 36) copyAssetIfDifferent(asset, new File(frameworkDir, "1.apk"));
            }

            // Shared-storage mirrors are for visibility/CLI paths only. Android 10+
            // forbids executing writable app payloads, so Apktool executes AAPT2 from
            // nativeLibraryDir where the APK installer placed the arm64 binaries.
            mirrorNative(isLargePageDevice() ? "libaapt2_33.so" : "libaapt2_35.so", new File(aaptDir, "aapt2"));
            mirrorNative("libaapt2.so", new File(new File(aaptDir, "legacy"), "aapt2"));
            mirrorNative("libaapt2_33.so", new File(new File(aaptDir, "sdk33"), "aapt2"));
            mirrorNative("libaapt2_35.so", new File(new File(aaptDir, "sdk35"), "aapt2"));
            ensureDebugKeystore();

            System.setProperty("apktool.android.framework.dir", frameworkDir.getAbsolutePath());
            System.setProperty("apktool.android.aapt2", getAaptBinary("default").getAbsolutePath());
        
        }
    }

    private void configureAndroidRuntimeProperties() {
        // Apktool uses java.io.tmpdir for extracted/temp files. App cache is the
        // Android-safe executable-independent temporary location.
        System.setProperty("java.io.tmpdir", context.getCacheDir().getAbsolutePath());
        File filesDir = context.getFilesDir();
        File dataDir = filesDir.getParentFile() == null ? filesDir : filesDir.getParentFile();
        System.setProperty("apktool.android.framework.dir", frameworkDir.getAbsolutePath());
        System.setProperty("apktool.android.home", root.getAbsolutePath());
        System.setProperty("apktool.android.dataDir", dataDir.getAbsolutePath());
        System.setProperty("apktool.android.nativeLibraryDir", context.getApplicationInfo().nativeLibraryDir);
        System.setProperty("apktool.android.rootDir", System.getenv("ANDROID_ROOT") == null ? "/system" : System.getenv("ANDROID_ROOT"));
        System.setProperty("smali.console.width", System.getProperty("smali.console.width", "100"));
        System.setProperty("jdk.nio.zipfs.allowDotZipEntry", "true");
        System.setProperty("jdk.util.zip.disableZip64ExtraFieldValidation", "true");
    }

    private void requireArm64Payload() throws IOException {
        if (Build.SUPPORTED_ABIS.length == 0 || !"arm64-v8a".equals(Build.SUPPORTED_ABIS[0])) {
            throw new IOException("This Apktool Android build is arm64-v8a only. Device primary ABI: "
                    + (Build.SUPPORTED_ABIS.length == 0 ? "unknown" : Build.SUPPORTED_ABIS[0]));
        }
        String[] required = {"libaapt2.so", "libaapt2_33.so", "libaapt2_35.so"};
        File nativeDir = new File(context.getApplicationInfo().nativeLibraryDir);
        for (String name : required) {
            File file = new File(nativeDir, name);
            if (!file.isFile()) throw new IOException("arm64-v8a AAPT2 payload is missing: " + file);
            if (file.length() == 0L) throw new IOException("arm64-v8a AAPT2 payload is empty: " + file);
        }
    }

    public Config newConfig() throws IOException {
        Config c = new Config(VERSION);
        c.setJobs(Math.max(1, Math.min(4, Runtime.getRuntime().availableProcessors())));
        c.setFrameworkDirectory(frameworkDir.getAbsolutePath());
        c.setAaptBinary(getAaptBinary("default").getAbsolutePath());
        return c;
    }

    /**
     * Returns an installed arm64 AAPT2 executable. On a 16 KiB page device the
     * 64 KiB-aligned bundled SDK33 payload is selected automatically; on normal
     * 4 KiB devices the newer SDK35 payload is the default. `legacy` exposes the
     * original bundled binary explicitly.
     */
    public File getAaptBinary(String variant) throws IOException {
        String v = variant == null ? "default" : variant.toLowerCase();
        String name;
        switch (v) {
            case "sdk33": name = "libaapt2_33.so"; break;
            case "sdk35": name = "libaapt2_35.so"; break;
            case "sdk36": name = isLargePageDevice() ? "libaapt2_33.so" : "libaapt2_35.so"; break;
            case "legacy": name = "libaapt2.so"; break;
            case "default":
                name = isLargePageDevice() ? "libaapt2_33.so" : "libaapt2_35.so";
                break;
            default:
                throw new IOException("Unknown AAPT2 variant: " + variant);
        }
        File file = new File(context.getApplicationInfo().nativeLibraryDir, name);
        if (!file.isFile()) throw new IOException("Bundled arm64-v8a AAPT2 missing: " + file);
        if (file.length() == 0L) throw new IOException("Bundled AAPT2 is empty: " + file);
        if (!file.canExecute() && !file.setExecutable(true, false)) {
            throw new IOException("Bundled AAPT2 is not executable: " + file);
        }
        return file;
    }

    public File getDebugKeystore() throws IOException {
        return ensureDebugKeystore();
    }

    private File ensureDebugKeystore() throws IOException {
        File key = new File(context.getFilesDir(), "apktool-debug.keystore");
        if (!key.isFile() || key.length() == 0) {
            try (InputStream in = context.getAssets().open("debug.keystore");
                 OutputStream out = new FileOutputStream(key)) {
                copy(in, out);
            }
        }
        return key;
    }

    public List<File> listFrameworks() {
        File[] files = frameworkDir.listFiles((dir, name) -> name.matches("\\d+(?:-[A-Za-z0-9._-]+)?\\.apk"));
        if (files == null) return new ArrayList<>();
        Arrays.sort(files, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        return new ArrayList<>(Arrays.asList(files));
    }

    public int deleteFrameworkFiles(List<String> names) throws IOException {
        synchronized (PROVISION_LOCK) {
            ensureDirectories();
        int deleted = 0;
        String rootPath = frameworkDir.getCanonicalPath() + File.separator;
        for (String name : names) {
            if (name == null || name.trim().isEmpty()) continue;
            File file = new File(frameworkDir, new File(name).getName());
            if (!file.getCanonicalPath().startsWith(rootPath)) throw new IOException("Invalid framework file: " + name);
            if (file.isFile()) {
                if (!file.delete()) throw new IOException("Cannot delete framework: " + file);
                deleted++;
            }
        }
            return deleted;
        }
    }

    public void resetFrameworks() throws IOException {
        synchronized (PROVISION_LOCK) {
            ensureDirectories();
        File[] files = frameworkDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".apk"));
        if (files != null) {
            for (File file : files) {
                if (!file.delete() && file.exists()) throw new IOException("Cannot delete framework: " + file);
            }
        }
            provision();
        }
    }

    public List<File> listProjects() {
        File[] files = projectsDir.listFiles(File::isDirectory);
        if (files == null) return new ArrayList<>();
        Arrays.sort(files, (a, b) -> Long.compare(a.lastModified(), b.lastModified()));
        return new ArrayList<>(Arrays.asList(files));
    }

    public File copyIntoInput(InputStream input, String displayName) throws IOException {
        ensureDirectories();
        String safe = sanitizeName(displayName == null ? "input.bin" : displayName);
        File dest = uniqueFile(inputDir, safe);
        try (OutputStream out = new FileOutputStream(dest)) {
            copy(input, out);
        }
        return dest;
    }

    public File uniqueOutput(String name) throws IOException {
        ensureDirectories();
        return uniqueFile(outputDir, sanitizeName(name));
    }

    public void ensureDirectories() throws IOException {
        for (File f : new File[]{root, binDir, aaptDir, frameworkDir, inputDir, projectsDir, outputDir, logsDir}) {
            if (!f.isDirectory() && !f.mkdirs()) throw new IOException("Cannot create " + f);
        }
    }

    private void copyAssetIfDifferent(String asset, File dst) throws IOException {
        AssetManager assets = context.getAssets();
        if (dst.isFile()) {
            byte[] bundled;
            byte[] installed;
            try (InputStream in = assets.open(asset, AssetManager.ACCESS_STREAMING)) { bundled = sha256(in); }
            try (InputStream in = new FileInputStream(dst)) { installed = sha256(in); }
            if (Arrays.equals(bundled, installed)) return;
        }
        File parent = dst.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) throw new IOException("Cannot create " + parent);
        File tmp = new File(dst.getParentFile(), dst.getName() + ".tmp");
        try (InputStream in = assets.open(asset, AssetManager.ACCESS_STREAMING);
             OutputStream out = new FileOutputStream(tmp)) {
            copy(in, out);
        }
        if (dst.exists() && !dst.delete()) throw new IOException("Cannot replace " + dst);
        if (!tmp.renameTo(dst)) {
            try (InputStream in = new FileInputStream(tmp); OutputStream out = new FileOutputStream(dst)) { copy(in, out); }
            //noinspection ResultOfMethodCallIgnored
            tmp.delete();
        }
    }

    private static byte[] sha256(InputStream in) throws IOException {
        final MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException impossible) {
            throw new IOException("SHA-256 unavailable", impossible);
        }
        byte[] buffer = new byte[64 * 1024];
        for (int n; (n = in.read(buffer)) >= 0; ) if (n > 0) digest.update(buffer, 0, n);
        return digest.digest();
    }

    private void mirrorNative(String libraryName, File dst) throws IOException {
        File src = new File(context.getApplicationInfo().nativeLibraryDir, libraryName);
        if (!src.isFile()) throw new IOException("Missing native payload " + src);
        File parent = dst.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) throw new IOException("Cannot create " + parent);
        if (!dst.isFile() || dst.length() != src.length()) {
            try (InputStream in = new FileInputStream(src); OutputStream out = new FileOutputStream(dst)) { copy(in, out); }
        }
        // Mirror may live on noexec shared storage; executable bit is informational.
        //noinspection ResultOfMethodCallIgnored
        dst.setExecutable(true, false);
    }

    private static String sanitizeName(String name) {
        String safe = name.replaceAll("[^A-Za-z0-9._() +@-]", "_");
        return safe.isEmpty() ? "input.bin" : safe;
    }

    public static File uniqueFile(File dir, String name) {
        File f = new File(dir, name);
        if (!f.exists()) return f;
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        String ext = dot > 0 ? name.substring(dot) : "";
        for (int i = 1; ; i++) {
            f = new File(dir, base + "-" + i + ext);
            if (!f.exists()) return f;
        }
    }

    public static void copy(InputStream in, OutputStream out) throws IOException {
        byte[] buf = new byte[1024 * 1024];
        for (int n; (n = in.read(buf)) >= 0; ) if (n > 0) out.write(buf, 0, n);
    }
}
