package io.github.lootdev78.mtapktool.antisplit;

import com.reandroid.apk.ApkBundle;
import com.reandroid.apk.ApkModule;
import com.reandroid.apkeditor.common.AndroidManifestHelper;
import com.reandroid.app.AndroidManifest;
import com.reandroid.archive.ZipEntryMap;
import com.reandroid.arsc.chunk.TableBlock;
import com.reandroid.arsc.chunk.xml.AndroidManifestBlock;
import com.reandroid.arsc.chunk.xml.ResXmlAttribute;
import com.reandroid.arsc.chunk.xml.ResXmlElement;
import com.reandroid.arsc.container.SpecTypePair;
import com.reandroid.arsc.model.ResourceEntry;
import com.reandroid.arsc.value.Entry;
import com.reandroid.arsc.value.ResValue;
import com.reandroid.arsc.value.ValueType;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Android-library integration of AntiSplit-M's REAndroid merge engine.
 *
 * The original AntiSplit-M project ships its merger as part of an Activity.  MTApktool keeps
 * the upstream merge implementation in this module, but exposes it through this UI-independent
 * facade so Explorer jobs can run in MTApktool's own task workflow.
 */
public final class AntiSplitEngine {
    private AntiSplitEngine() {}

    public interface ProgressListener {
        void onMessage(String message);
    }

    public static final class Options {
        public int compressionLevel = 6;
        public boolean forceMerge = false; // AntiSplit-M "force": continue despite version-code mismatch
        public boolean stripSplitMetadata = true;
        public boolean removeMetaInf = true;
    }

    public static File mergeContainer(
            File container,
            File output,
            Collection<String> selectedEntryPaths,
            Options options,
            ProgressListener listener
    ) throws Exception {
        if (container == null || !container.isFile()) {
            throw new IOException("Split container not found: " + container);
        }
        if (output == null) throw new IOException("Output path is missing");
        Options actual = options == null ? new Options() : options;
        File parent = output.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
            throw new IOException("Cannot create output directory: " + parent);
        }

        File workspaceParent = parent != null ? parent : container.getParentFile();
        if (workspaceParent == null) workspaceParent = new File(".");
        File work = new File(workspaceParent, ".antisplit-" + System.nanoTime());
        if (!work.mkdirs()) throw new IOException("Cannot create AntiSplit workspace: " + work);

        try {
            message(listener, "AntiSplit-M: extracting selected APK modules");
            extractSelectedApks(container, work, selectedEntryPaths);

            try (ApkBundle bundle = new ApkBundle(actual.compressionLevel)) {
                bundle.loadApkDirectory(work);
                message(listener, "AntiSplit-M: merging " + bundle.countModules() + " module(s)");
                try (ApkModule merged = bundle.mergeModules(!actual.forceMerge)) {
                    // A merge always invalidates source signatures. MTApktool's signing workflow can sign
                    // the produced file afterwards, while zipalign is handled by the caller.
                    merged.setApkSignatureBlock(null);
                    if (actual.stripSplitMetadata && merged.hasAndroidManifest()) {
                        message(listener, "AntiSplit-M: sanitizing split metadata");
                        sanitizeSplitManifest(merged);
                    }
                    if (actual.removeMetaInf) {
                        removeMetaInf(merged.getZipEntryMap());
                    }
                    if (output.exists() && !output.delete()) {
                        throw new IOException("Cannot replace output: " + output);
                    }
                    merged.writeApk(output);
                }
            }
            if (!output.isFile() || output.length() == 0L) {
                throw new IOException("AntiSplit-M did not produce an APK");
            }
            message(listener, "AntiSplit-M: merged APK created");
            return output;
        } finally {
            deleteRecursively(work);
        }
    }

    public static File mergeApkFiles(
            Collection<File> apkFiles,
            File output,
            Options options,
            ProgressListener listener
    ) throws Exception {
        if (apkFiles == null || apkFiles.isEmpty()) throw new IOException("No APK modules selected");
        File parent = output.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
            throw new IOException("Cannot create output directory: " + parent);
        }
        File work = new File(parent != null ? parent : new File("."), ".antisplit-files-" + System.nanoTime());
        if (!work.mkdirs()) throw new IOException("Cannot create AntiSplit workspace: " + work);
        try {
            int index = 0;
            for (File apk : apkFiles) {
                if (apk == null || !apk.isFile()) continue;
                File copy = new File(work, String.format(Locale.ROOT, "%03d-%s", index++, apk.getName()));
                copy(apk, copy);
            }
            Options actual = options == null ? new Options() : options;
            try (ApkBundle bundle = new ApkBundle(actual.compressionLevel)) {
                bundle.loadApkDirectory(work);
                message(listener, "AntiSplit-M: merging " + bundle.countModules() + " module(s)");
                try (ApkModule merged = bundle.mergeModules(!actual.forceMerge)) {
                    merged.setApkSignatureBlock(null);
                    if (actual.stripSplitMetadata && merged.hasAndroidManifest()) sanitizeSplitManifest(merged);
                    if (actual.removeMetaInf) removeMetaInf(merged.getZipEntryMap());
                    if (output.exists() && !output.delete()) throw new IOException("Cannot replace output: " + output);
                    merged.writeApk(output);
                }
            }
            return output;
        } finally {
            deleteRecursively(work);
        }
    }

    private static void extractSelectedApks(File container, File directory, Collection<String> selectedEntryPaths) throws IOException {
        Set<String> selected = selectedEntryPaths == null ? null : new HashSet<>(selectedEntryPaths);
        int count = 0;
        try (ZipFile zip = new ZipFile(container)) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry.isDirectory()) continue;
                String name = entry.getName();
                if (!name.toLowerCase(Locale.ROOT).endsWith(".apk")) continue;
                if (selected != null && !selected.isEmpty() && !selected.contains(name)) continue;
                String safeName = new File(name).getName();
                if (safeName.isEmpty()) safeName = "split-" + count + ".apk";
                File out = uniqueFile(directory, safeName);
                try (InputStream in = zip.getInputStream(entry); FileOutputStream fos = new FileOutputStream(out)) {
                    byte[] buffer = new byte[1024 * 1024];
                    int read;
                    while ((read = in.read(buffer)) >= 0) {
                        if (read > 0) fos.write(buffer, 0, read);
                    }
                }
                count++;
            }
        }
        if (count == 0) throw new IOException("No selected APK modules found in " + container.getName());
    }

    /** Mirrors AntiSplit-M's extra manifest cleanup on top of APKEditor's own bundle sanitizer. */
    private static void sanitizeSplitManifest(ApkModule mergedModule) {
        AndroidManifestBlock manifest = mergedModule.getAndroidManifest();
        if (manifest == null) return;

        AndroidManifestHelper.removeAttributeFromManifestById(manifest, AndroidManifest.ID_requiredSplitTypes, null);
        AndroidManifestHelper.removeAttributeFromManifestById(manifest, AndroidManifest.ID_splitTypes, null);
        AndroidManifestHelper.removeAttributeFromManifestByName(manifest, AndroidManifest.NAME_splitTypes, null);
        AndroidManifestHelper.removeAttributeFromManifestByName(manifest, AndroidManifest.NAME_requiredSplitTypes, null);
        AndroidManifestHelper.removeAttributeFromManifestAndApplication(
                manifest, AndroidManifest.ID_extractNativeLibs, null, AndroidManifest.NAME_extractNativeLibs);
        AndroidManifestHelper.removeAttributeFromManifestAndApplication(
                manifest, AndroidManifest.ID_isSplitRequired, null, AndroidManifest.NAME_isSplitRequired);

        ResXmlElement application = manifest.getApplicationElement();
        if (application != null) {
            List<ResXmlElement> splitMetaDataElements = new ArrayList<>(AndroidManifestHelper.listSplitRequired(application));
            boolean splitsRemoved = false;
            for (ResXmlElement meta : splitMetaDataElements) {
                if (!splitsRemoved) {
                    boolean result = false;
                    ResXmlAttribute nameAttribute = meta.searchAttributeByResourceId(AndroidManifest.ID_name);
                    if (nameAttribute != null && "com.android.vending.splits".equals(nameAttribute.getValueAsString())) {
                        ResXmlAttribute valueAttribute = meta.searchAttributeByResourceId(AndroidManifest.ID_value);
                        if (valueAttribute == null) valueAttribute = meta.searchAttributeByResourceId(AndroidManifest.ID_resource);
                        if (valueAttribute != null && valueAttribute.getValueType() == ValueType.REFERENCE && mergedModule.hasTableBlock()) {
                            TableBlock tableBlock = mergedModule.getTableBlock();
                            ResourceEntry resourceEntry = tableBlock.getResource(valueAttribute.getData());
                            if (resourceEntry != null) {
                                ZipEntryMap zipEntryMap = mergedModule.getZipEntryMap();
                                for (Entry entry : resourceEntry) {
                                    if (entry == null) continue;
                                    ResValue resValue = entry.getResValue();
                                    if (resValue == null) continue;
                                    String path = resValue.getValueAsString();
                                    if (path != null) zipEntryMap.remove(path);
                                    entry.setNull(true);
                                    SpecTypePair pair = entry.getTypeBlock().getParentSpecTypePair();
                                    pair.removeNullEntries(entry.getId());
                                }
                                result = true;
                            }
                        }
                    }
                    splitsRemoved = result;
                }
                application.remove(meta);
            }
        }
        manifest.refresh();
    }

    private static void removeMetaInf(ZipEntryMap map) {
        if (map == null) return;
        // ZipEntryMap exposes path based removal; collect first to avoid mutating while iterating.
        List<String> remove = new ArrayList<>();
        for (com.reandroid.archive.InputSource source : map.toArray()) {
            if (source == null) continue;
            String name = source.getAlias();
            if (name == null) name = source.getName();
            if (name != null && name.toUpperCase(Locale.ROOT).startsWith("META-INF/")) remove.add(name);
        }
        for (String name : remove) map.remove(name);
    }

    private static File uniqueFile(File directory, String name) {
        File out = new File(directory, name);
        if (!out.exists()) return out;
        String stem = name;
        String ext = "";
        int dot = name.lastIndexOf('.');
        if (dot > 0) {
            stem = name.substring(0, dot);
            ext = name.substring(dot);
        }
        int i = 1;
        do {
            out = new File(directory, stem + "-" + i++ + ext);
        } while (out.exists());
        return out;
    }

    private static void copy(File source, File destination) throws IOException {
        try (InputStream in = new java.io.FileInputStream(source); FileOutputStream out = new FileOutputStream(destination)) {
            byte[] buffer = new byte[1024 * 1024];
            int read;
            while ((read = in.read(buffer)) >= 0) if (read > 0) out.write(buffer, 0, read);
        }
    }

    private static void deleteRecursively(File file) {
        if (file == null || !file.exists()) return;
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) for (File child : children) deleteRecursively(child);
        }
        //noinspection ResultOfMethodCallIgnored
        file.delete();
    }

    private static void message(ProgressListener listener, String message) {
        if (listener != null) listener.onMessage(message);
    }
}
