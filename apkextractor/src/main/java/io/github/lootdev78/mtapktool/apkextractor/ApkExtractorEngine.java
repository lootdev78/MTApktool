package io.github.lootdev78.mtapktool.apkextractor;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Environment;

import io.github.lootdev78.mtapktool.antisplit.AntiSplitEngine;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Host-facing extraction engine; UI is provided by MTApktool Compose. */
public final class ApkExtractorEngine {
    private ApkExtractorEngine() {}

    public static final class AppEntry {
        public final String packageName;
        public final String label;
        public final String versionName;
        public final long versionCode;
        public final boolean system;
        public final boolean split;
        public final String sourceDir;
        public final String dataDir;
        public final long firstInstallTime;
        public final long lastUpdateTime;
        public final int uid;
        public final int minSdk;
        public final int targetSdk;
        public final long baseSize;
        public final long splitSize;

        public AppEntry(String packageName, String label, String versionName, long versionCode,
                        boolean system, boolean split, String sourceDir, String dataDir,
                        long firstInstallTime, long lastUpdateTime, int uid, int minSdk, int targetSdk,
                        long baseSize, long splitSize) {
            this.packageName = packageName;
            this.label = label;
            this.versionName = versionName;
            this.versionCode = versionCode;
            this.system = system;
            this.split = split;
            this.sourceDir = sourceDir;
            this.dataDir = dataDir;
            this.firstInstallTime = firstInstallTime;
            this.lastUpdateTime = lastUpdateTime;
            this.uid = uid;
            this.minSdk = minSdk;
            this.targetSdk = targetSdk;
            this.baseSize = baseSize;
            this.splitSize = splitSize;
        }
    }

    public static File defaultOutputRoot() {
        return new File(Environment.getExternalStorageDirectory(), "apktool/apks");
    }

    public static List<AppEntry> listInstalled(Context context, boolean includeSystem) {
        PackageManager pm = context.getPackageManager();
        List<PackageInfo> packages;
        if (Build.VERSION.SDK_INT >= 33) packages = pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(0));
        else {
            //noinspection deprecation
            packages = pm.getInstalledPackages(0);
        }
        List<AppEntry> result = new ArrayList<>();
        for (PackageInfo info : packages) {
            ApplicationInfo app = info.applicationInfo;
            if (app == null || app.sourceDir == null) continue;
            boolean system = (app.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
            if (!includeSystem && system) continue;
            String label;
            try { label = String.valueOf(pm.getApplicationLabel(app)); }
            catch (Throwable t) { label = info.packageName; }
            long baseSize = new File(app.sourceDir).length();
            long splitSize = 0L;
            if (app.splitSourceDirs != null) {
                for (String splitPath : app.splitSourceDirs) {
                    if (splitPath != null) splitSize += new File(splitPath).length();
                }
            }
            result.add(new AppEntry(
                    info.packageName,
                    label,
                    info.versionName == null ? "" : info.versionName,
                    info.getLongVersionCode(),
                    system,
                    app.splitSourceDirs != null && app.splitSourceDirs.length > 0,
                    app.sourceDir,
                    app.dataDir,
                    info.firstInstallTime,
                    info.lastUpdateTime,
                    app.uid,
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.N ? app.minSdkVersion : 0,
                    app.targetSdkVersion,
                    baseSize,
                    splitSize
            ));
        }
        Collections.sort(result, Comparator.comparing(a -> a.label.toLowerCase(Locale.ROOT)));
        return result;
    }

    public static File extractBase(Context context, String packageName, File root) throws Exception {
        return extractBase(context, packageName, root, safe(packageName) + ".apk");
    }

    public static File extractBase(Context context, String packageName, File root, String outputName) throws Exception {
        ApplicationInfo app = packageInfo(context, packageName).applicationInfo;
        if (app == null || app.sourceDir == null) throw new IOException("Base APK unavailable");
        File outRoot = ensureRoot(root);
        String name = normalizeName(outputName, safe(packageName) + ".apk", ".apk");
        File out = unique(outRoot, name);
        copy(new File(app.sourceDir), out);
        return out;
    }

    public static File createApks(Context context, String packageName, File root, int compression) throws Exception {
        return createApks(context, packageName, root, compression, safe(packageName) + ".apks");
    }

    public static File createApks(Context context, String packageName, File root, int compression, String outputName) throws Exception {
        List<File> modules = installedFiles(context, packageName);
        File outRoot = ensureRoot(root);
        String name = normalizeName(outputName, safe(packageName) + ".apks", ".apks");
        File out = unique(outRoot, name);
        try (ZipOutputStream zip = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(out)))) {
            zip.setLevel(Math.max(0, Math.min(9, compression)));
            for (int i = 0; i < modules.size(); i++) {
                File module = modules.get(i);
                ZipEntry entry = new ZipEntry(i == 0 ? "base.apk" : module.getName());
                zip.putNextEntry(entry);
                try (InputStream in = new FileInputStream(module)) { copy(in, zip); }
                zip.closeEntry();
            }
        }
        return out;
    }

    public static File mergeToApk(Context context, String packageName, File root, int compression, boolean force) throws Exception {
        return mergeToApk(context, packageName, root, compression, force, safe(packageName) + ".apk");
    }

    public static File mergeToApk(Context context, String packageName, File root, int compression, boolean force, String outputName) throws Exception {
        List<File> modules = installedFiles(context, packageName);
        if (modules.size() <= 1) return extractBase(context, packageName, root, outputName);
        File outRoot = ensureRoot(root);
        File tempContainer = File.createTempFile("mtapktool-extractor-", ".apks", context.getCacheDir());
        try {
            try (ZipOutputStream zip = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(tempContainer)))) {
                zip.setLevel(Math.max(0, Math.min(9, compression)));
                for (int i = 0; i < modules.size(); i++) {
                    File module = modules.get(i);
                    ZipEntry entry = new ZipEntry(i == 0 ? "base.apk" : module.getName());
                    zip.putNextEntry(entry);
                    try (InputStream in = new FileInputStream(module)) { copy(in, zip); }
                    zip.closeEntry();
                }
            }
            String name = normalizeName(outputName, safe(packageName) + ".apk", ".apk");
            File out = unique(outRoot, name);
            AntiSplitEngine.Options options = new AntiSplitEngine.Options();
            options.compressionLevel = Math.max(0, Math.min(9, compression));
            options.forceMerge = force;
            options.cleanMetaInf = true;
            return AntiSplitEngine.mergeContainer(tempContainer, out, null, options, null);
        } finally {
            //noinspection ResultOfMethodCallIgnored
            tempContainer.delete();
        }
    }

    private static List<File> installedFiles(Context context, String packageName) throws Exception {
        ApplicationInfo app = packageInfo(context, packageName).applicationInfo;
        if (app == null || app.sourceDir == null) throw new IOException("APK path unavailable");
        List<File> result = new ArrayList<>();
        result.add(new File(app.sourceDir));
        if (app.splitSourceDirs != null) for (String value : app.splitSourceDirs) if (value != null) result.add(new File(value));
        return result;
    }

    public static List<File> installedApkFiles(Context context, String packageName) throws Exception {
        return new ArrayList<>(installedFiles(context, packageName));
    }

    private static String normalizeName(String candidate, String fallback, String requiredExtension) {
        String value = candidate == null ? "" : candidate.trim();
        if (value.isEmpty()) value = fallback;
        value = value.replace('/', '_').replace('\\', '_');
        if (!value.toLowerCase(Locale.ROOT).endsWith(requiredExtension)) {
            int dot = value.lastIndexOf('.');
            if (dot > 0) value = value.substring(0, dot);
            value += requiredExtension;
        }
        return value;
    }

    private static PackageInfo packageInfo(Context context, String packageName) throws PackageManager.NameNotFoundException {
        PackageManager pm = context.getPackageManager();
        if (Build.VERSION.SDK_INT >= 33) return pm.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0));
        //noinspection deprecation
        return pm.getPackageInfo(packageName, 0);
    }

    private static File ensureRoot(File root) throws IOException {
        File value = root == null ? defaultOutputRoot() : root;
        if (!value.isDirectory() && !value.mkdirs()) throw new IOException("Cannot create " + value);
        return value;
    }

    private static File unique(File dir, String name) {
        File out = new File(dir, name);
        if (!out.exists()) return out;
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        String ext = dot > 0 ? name.substring(dot) : "";
        int i = 1;
        while (out.exists()) out = new File(dir, base + " (" + i++ + ")" + ext);
        return out;
    }

    private static String safe(String value) { return value.replaceAll("[^A-Za-z0-9._-]+", "_"); }
    private static void copy(File source, File target) throws IOException {
        try (InputStream in = new FileInputStream(source); FileOutputStream out = new FileOutputStream(target)) { copy(in, out); }
    }
    private static void copy(InputStream in, java.io.OutputStream out) throws IOException {
        byte[] buffer = new byte[1024 * 1024];
        int read;
        while ((read = in.read(buffer)) >= 0) if (read > 0) out.write(buffer, 0, read);
    }
}
