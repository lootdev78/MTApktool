package com.reandroid.apkeditor.protect;

import java.util.HashSet;
import java.util.Set;

/** MTAPKTool's typed options for the Apache-licensed REAndroid confusers. */
public final class ProtectorOptions {
    public boolean skipManifest;
    public boolean confuse_zip;
    public int dexLevel;
    public final Set<String> keepTypes = new HashSet<>();

    public ProtectorOptions() { keepTypes.add("font"); }
    public boolean isKeepType(String type) { return isKeepAllTypes() || keepTypes.contains(type); }
    public boolean isKeepAllTypes() { return keepTypes.contains("all-types"); }
    public String[] loadDirectoryNameDictionary() {
        return new String[]{"AndroidManifest.xml", "resources.arsc", "classes.dex", "kotlin", "META-INF", "res/values/strings.xml"};
    }
    public String[] loadFileNameDictionary() {
        return new String[]{".", "//", "///", " ", "classes.dex", "AndroidManifest.xml", "resources.arsc"};
    }
}
