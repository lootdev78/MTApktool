package io.github.lootdev78.mtapktool.antisplit;

import com.reandroid.apk.ApkBundle;
import com.reandroid.apk.ApkModule;
import com.reandroid.archive.InputSource;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** UI-independent integration of the supplied AntiSplit-M / REAndroid merge engine. */
public final class AntiSplitEngine {
    private AntiSplitEngine() {}

    public static final class Options {
        public int compressionLevel = 6;
        public boolean forceMerge = false;
        public boolean cleanMetaInf = true;
    }

    public interface Listener { void onLine(String line); }

    public static File mergeContainer(File container, File output, Collection<String> selectedEntries,
                                      Options options, Listener listener) throws Exception {
        if (container == null || !container.isFile()) throw new IOException("Split container not found");
        Options actual = options == null ? new Options() : options;
        File parent = output.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) throw new IOException("Cannot create output directory");
        File work = new File(parent != null ? parent : container.getParentFile(), ".antisplit-" + System.nanoTime());
        if (!work.mkdirs()) throw new IOException("Cannot create AntiSplit workspace");
        try {
            extractSelected(container, work, selectedEntries);
            line(listener, "AntiSplit-M: loading APK modules");
            try (ApkBundle bundle = new ApkBundle(actual.compressionLevel)) {
                bundle.loadApkDirectory(work);
                line(listener, "AntiSplit-M: merging " + bundle.countModules() + " module(s)");
                try (ApkModule merged = bundle.mergeModules(!actual.forceMerge)) {
                    merged.setApkSignatureBlock(null);
                    if (actual.cleanMetaInf) {
                        merged.getZipEntryMap().removeIf(source -> {
                            String name = source.getAlias();
                            if (name == null) name = source.getName();
                            if (name == null) return false;
                            String upper = name.toUpperCase(Locale.ROOT);
                            return upper.equals("META-INF/MANIFEST.MF") || upper.endsWith(".SF") ||
                                    upper.endsWith(".RSA") || upper.endsWith(".DSA") || upper.endsWith(".EC");
                        });
                    }
                    if (output.exists() && !output.delete()) throw new IOException("Cannot replace output");
                    merged.writeApk(output);
                }
            }
            if (!output.isFile() || output.length() == 0L) throw new IOException("AntiSplit-M did not create an APK");
            line(listener, "AntiSplit-M: APK created");
            return output;
        } finally {
            delete(work);
        }
    }

    private static void extractSelected(File container, File dir, Collection<String> selectedEntries) throws IOException {
        Set<String> selected = selectedEntries == null ? null : new HashSet<>(selectedEntries);
        int count = 0;
        try (ZipFile zip = new ZipFile(container)) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry.isDirectory() || !entry.getName().toLowerCase(Locale.ROOT).endsWith(".apk")) continue;
                if (selected != null && !selected.isEmpty() && !selected.contains(entry.getName())) continue;
                File out = unique(dir, new File(entry.getName()).getName());
                try (InputStream in = zip.getInputStream(entry); FileOutputStream fos = new FileOutputStream(out)) {
                    byte[] buf = new byte[1024 * 1024];
                    int read;
                    while ((read = in.read(buf)) >= 0) if (read > 0) fos.write(buf, 0, read);
                }
                count++;
            }
        }
        if (count == 0) throw new IOException("No APK modules selected");
    }

    private static File unique(File dir, String name) {
        File out = new File(dir, name);
        if (!out.exists()) return out;
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        String ext = dot > 0 ? name.substring(dot) : "";
        int i = 1;
        while (out.exists()) out = new File(dir, base + "-" + i++ + ext);
        return out;
    }

    private static void delete(File file) {
        if (file == null || !file.exists()) return;
        File[] children = file.listFiles();
        if (children != null) for (File child : children) delete(child);
        file.delete();
    }

    private static void line(Listener listener, String value) { if (listener != null) listener.onLine(value); }
}
