package io.github.lootdev78.mtapktool.apkextractor;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Environment;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;

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
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/** Integrated APKExtractor engine used by MTApktool's navigation screen. */
public final class ApkExtractorEngine {
    private ApkExtractorEngine() {}

    public interface ProgressListener {
        void onMessage(String message);
    }

    public enum SplitMode {
        APKS_ARCHIVE,
        MERGED_APK,
        BASE_APK_ONLY
    }

    public static final class AppEntry {
        public final String packageName;
        public final String label;
        public final String versionName;
        public final long versionCode;
        public final long firstInstallTime;
        public final long lastUpdateTime;
        public final boolean system;
        public final boolean split;

        public AppEntry(String packageName, String label, String versionName, long versionCode, long firstInstallTime,
                        long lastUpdateTime, boolean system, boolean split) {
            this.packageName = packageName;
            this.label = label;
            this.versionName = versionName;
            this.versionCode = versionCode;
            this.firstInstallTime = firstInstallTime;
            this.lastUpdateTime = lastUpdateTime;
            this.system = system;
            this.split = split;
        }
    }

    public static File defaultOutputRoot() {
        return new File(Environment.getExternalStorageDirectory(), "apktool/apks");
    }

    public static List<AppEntry> listInstalledApps(Context context, boolean includeSystem) {
        PackageManager pm = context.getPackageManager();
        List<PackageInfo> packages;
        if (Build.VERSION.SDK_INT >= 33) {
            packages = pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(0));
        } else {
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
            try {
                CharSequence value = pm.getApplicationLabel(app);
                label = value == null ? info.packageName : value.toString();
            } catch (Throwable ignored) {
                label = info.packageName;
            }
            result.add(new AppEntry(
                    info.packageName,
                    label,
                    info.versionName == null ? "" : info.versionName,
                    info.getLongVersionCode(),
                    info.firstInstallTime,
                    info.lastUpdateTime,
                    system,
                    app.splitSourceDirs != null && app.splitSourceDirs.length > 0
            ));
        }
        Collections.sort(result, Comparator.comparing(a -> a.label.toLowerCase(Locale.ROOT)));
        return result;
    }

    public static File extract(
            Context context,
            String packageName,
            File outputRoot,
            SplitMode splitMode,
            int compressionLevel,
            ProgressListener listener
    ) throws Exception {
        return extract(context, packageName, outputRoot, splitMode, compressionLevel,
                false, true, true, listener);
    }

    public static File extract(
            Context context,
            String packageName,
            File outputRoot,
            SplitMode splitMode,
            int compressionLevel,
            boolean antiSplitForceMerge,
            boolean stripSplitMetadata,
            boolean removeMetaInf,
            ProgressListener listener
    ) throws Exception {
        PackageInfo packageInfo = getPackageInfo(context, packageName);
        ApplicationInfo app = packageInfo.applicationInfo;
        if (app == null || app.sourceDir == null) throw new IOException("APK path is unavailable for " + packageName);

        File root = ensureOutputRoot(outputRoot);

        String baseName = safeFileName(packageName);
        File base = new File(app.sourceDir);
        String[] splitPaths = app.splitSourceDirs;
        boolean hasSplits = splitPaths != null && splitPaths.length > 0;

        if (!hasSplits || splitMode == SplitMode.BASE_APK_ONLY) {
            File out = uniqueFile(root, baseName + ".apk");
            message(listener, "APKExtractor: copying base APK");
            copy(base, out);
            return out;
        }

        List<File> modules = new ArrayList<>();
        modules.add(base);
        for (String split : splitPaths) if (split != null) modules.add(new File(split));

        if (splitMode == SplitMode.MERGED_APK) {
            File out = uniqueFile(root, baseName + ".apk");
            message(listener, "APKExtractor: AntiSplit-M merge");
            AntiSplitEngine.Options options = new AntiSplitEngine.Options();
            options.compressionLevel = Math.max(0, Math.min(9, compressionLevel));
            options.forceMerge = antiSplitForceMerge;
            options.stripSplitMetadata = stripSplitMetadata;
            options.removeMetaInf = removeMetaInf;
            return AntiSplitEngine.mergeApkFiles(modules, out, options, listener == null ? null : listener::onMessage);
        }

        File out = uniqueFile(root, baseName + ".apks");
        message(listener, "APKExtractor: creating APKS archive");
        writeApksArchive(modules, out, compressionLevel);
        return out;
    }

    public static List<File> installedApkFiles(Context context, String packageName) throws Exception {
        PackageInfo packageInfo = getPackageInfo(context, packageName);
        ApplicationInfo app = packageInfo.applicationInfo;
        if (app == null || app.sourceDir == null) throw new IOException("APK path is unavailable for " + packageName);
        List<File> result = new ArrayList<>();
        result.add(new File(app.sourceDir));
        if (app.splitSourceDirs != null) {
            for (String split : app.splitSourceDirs) if (split != null) result.add(new File(split));
        }
        return result;
    }

    public static File extractIcon(Context context, String packageName, File outputRoot, ProgressListener listener) throws Exception {
        PackageManager pm = context.getPackageManager();
        PackageInfo packageInfo = getPackageInfo(context, packageName);
        ApplicationInfo app = packageInfo.applicationInfo;
        if (app == null) throw new IOException("ApplicationInfo is unavailable for " + packageName);
        File root = ensureOutputRoot(outputRoot);
        File output = uniqueFile(root, safeFileName(packageName) + "_icon.png");
        message(listener, "APKExtractor: extracting app icon");
        Drawable drawable = app.loadIcon(pm);
        Bitmap bitmap;
        if (drawable instanceof BitmapDrawable && ((BitmapDrawable) drawable).getBitmap() != null) {
            bitmap = ((BitmapDrawable) drawable).getBitmap();
        } else {
            int width = Math.max(1, drawable.getIntrinsicWidth());
            int height = Math.max(1, drawable.getIntrinsicHeight());
            bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            drawable.setBounds(0, 0, width, height);
            drawable.draw(canvas);
        }
        try (FileOutputStream stream = new FileOutputStream(output)) {
            if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)) throw new IOException("Failed to encode icon PNG");
        }
        return output;
    }

    public static File extractManifest(Context context, String packageName, File outputRoot, ProgressListener listener) throws Exception {
        List<File> modules = installedApkFiles(context, packageName);
        File root = ensureOutputRoot(outputRoot);
        File output = uniqueFile(root, safeFileName(packageName) + "_AndroidManifest.xml");
        message(listener, "APKExtractor: extracting AndroidManifest.xml");
        try (ZipFile zip = new ZipFile(modules.get(0))) {
            ZipEntry entry = zip.getEntry("AndroidManifest.xml");
            if (entry == null) throw new IOException("AndroidManifest.xml was not found");
            try (InputStream input = zip.getInputStream(entry); FileOutputStream out = new FileOutputStream(output)) {
                copyStream(input, out);
            }
        }
        return output;
    }

    public static File extractDex(Context context, String packageName, File outputRoot, int compressionLevel, ProgressListener listener) throws Exception {
        return extractMatchingEntries(context, packageName, outputRoot, "_dex.zip", compressionLevel,
                name -> name.matches("classes([0-9]+)?\\.dex"), "DEX", listener);
    }

    public static File extractResources(Context context, String packageName, File outputRoot, int compressionLevel, ProgressListener listener) throws Exception {
        return extractMatchingEntries(context, packageName, outputRoot, "_resources.zip", compressionLevel,
                name -> name.equals("resources.arsc") || name.startsWith("res/"), "resources", listener);
    }

    public static File extractLibraries(Context context, String packageName, File outputRoot, int compressionLevel, ProgressListener listener) throws Exception {
        return extractMatchingEntries(context, packageName, outputRoot, "_libs.zip", compressionLevel,
                name -> name.startsWith("lib/") && !name.endsWith("/"), "native libraries", listener);
    }

    public static File extractSplitApk(Context context, String packageName, int splitIndex, File outputRoot, ProgressListener listener) throws Exception {
        List<File> modules = installedApkFiles(context, packageName);
        if (splitIndex < 0 || splitIndex >= modules.size()) throw new IOException("Invalid split index: " + splitIndex);
        File root = ensureOutputRoot(outputRoot);
        File source = modules.get(splitIndex);
        String defaultName = splitIndex == 0 ? safeFileName(packageName) + "_base.apk" : safeFileName(packageName) + "_" + safeFileName(source.getName());
        File output = uniqueFile(root, defaultName);
        message(listener, "APKExtractor: copying " + source.getName());
        copy(source, output);
        return output;
    }

    private interface EntryMatcher { boolean matches(String name); }

    private static File extractMatchingEntries(
            Context context,
            String packageName,
            File outputRoot,
            String suffix,
            int compressionLevel,
            EntryMatcher matcher,
            String label,
            ProgressListener listener
    ) throws Exception {
        List<File> modules = installedApkFiles(context, packageName);
        File root = ensureOutputRoot(outputRoot);
        File output = uniqueFile(root, safeFileName(packageName) + suffix);
        int count = 0;
        message(listener, "APKExtractor: extracting " + label);
        try (ZipOutputStream zipOut = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(output)))) {
            zipOut.setLevel(Math.max(0, Math.min(9, compressionLevel)));
            for (int moduleIndex = 0; moduleIndex < modules.size(); moduleIndex++) {
                File module = modules.get(moduleIndex);
                if (!module.isFile()) continue;
                String modulePrefix = moduleIndex == 0 ? "base" : safeFileName(module.getName().replaceFirst("(?i)\\.apk$", ""));
                try (ZipFile zip = new ZipFile(module)) {
                    java.util.Enumeration<? extends ZipEntry> entries = zip.entries();
                    while (entries.hasMoreElements()) {
                        ZipEntry entry = entries.nextElement();
                        String name = entry.getName();
                        if (entry.isDirectory() || !matcher.matches(name)) continue;
                        ZipEntry outEntry = new ZipEntry(modulePrefix + "/" + name);
                        zipOut.putNextEntry(outEntry);
                        try (InputStream input = zip.getInputStream(entry)) { copyStream(input, zipOut); }
                        zipOut.closeEntry();
                        count++;
                    }
                }
            }
        }
        if (count == 0) {
            output.delete();
            throw new IOException("No " + label + " entries found");
        }
        return output;
    }

    private static PackageInfo getPackageInfo(Context context, String packageName) throws PackageManager.NameNotFoundException {
        PackageManager pm = context.getPackageManager();
        if (Build.VERSION.SDK_INT >= 33) {
            return pm.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0));
        }
        //noinspection deprecation
        return pm.getPackageInfo(packageName, 0);
    }

    private static File ensureOutputRoot(File outputRoot) throws IOException {
        File root = outputRoot == null ? defaultOutputRoot() : outputRoot;
        if (!root.isDirectory() && !root.mkdirs()) throw new IOException("Cannot create " + root);
        return root;
    }

    private static void copyStream(InputStream input, java.io.OutputStream output) throws IOException {
        byte[] buffer = new byte[1024 * 1024];
        int read;
        while ((read = input.read(buffer)) >= 0) if (read > 0) output.write(buffer, 0, read);
    }

    private static void writeApksArchive(List<File> modules, File output, int compressionLevel) throws IOException {
        try (ZipOutputStream zip = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(output)))) {
            zip.setLevel(Math.max(0, Math.min(9, compressionLevel)));
            for (int i = 0; i < modules.size(); i++) {
                File module = modules.get(i);
                if (!module.isFile()) continue;
                String name = i == 0 ? "base.apk" : module.getName();
                ZipEntry entry = new ZipEntry(name);
                entry.setMethod(ZipEntry.DEFLATED);
                zip.putNextEntry(entry);
                try (InputStream in = new FileInputStream(module)) {
                    byte[] buffer = new byte[1024 * 1024];
                    int read;
                    while ((read = in.read(buffer)) >= 0) if (read > 0) zip.write(buffer, 0, read);
                }
                zip.closeEntry();
            }
        }
    }

    private static void copy(File source, File destination) throws IOException {
        try (InputStream in = new FileInputStream(source); FileOutputStream out = new FileOutputStream(destination)) {
            byte[] buffer = new byte[1024 * 1024];
            int read;
            while ((read = in.read(buffer)) >= 0) if (read > 0) out.write(buffer, 0, read);
        }
    }

    private static File uniqueFile(File directory, String name) {
        String stem = name;
        String ext = "";
        int dot = name.lastIndexOf('.');
        if (dot > 0) { stem = name.substring(0, dot); ext = name.substring(dot); }
        File result = new File(directory, name);
        int index = 1;
        while (result.exists()) result = new File(directory, stem + " (" + index++ + ")" + ext);
        return result;
    }

    private static String safeFileName(String value) {
        return value.replaceAll("[^A-Za-z0-9._-]+", "_");
    }

    private static void message(ProgressListener listener, String message) {
        if (listener != null) listener.onMessage(message);
    }
}
