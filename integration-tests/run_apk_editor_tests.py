#!/usr/bin/env python3
"""Compile real REAndroid sources and exercise APK workflows on the JVM with Android shims.

Set MT_XMLPULL_JAR to xmlpull-1.1.3.4d.jar or the supplied APKEditor.jar.
Only the org/xmlpull API classes are extracted; no alternate REAndroid implementation is used.
This does not compile Android/Compose UI or exercise the injected code on a device.
"""
from pathlib import Path
import os
import re
import subprocess
import tempfile
import zipfile

root = Path(__file__).resolve().parents[1]
configured = os.environ.get("MT_XMLPULL_JAR")
candidates = [Path(configured)] if configured else []
candidates += list((Path.home() / ".gradle/caches/modules-2/files-2.1/xmlpull/xmlpull").glob("**/*.jar"))
candidates += [root.parent.parent / "mp-libs/libs/APKEditor.jar"]
dependency = next((p for p in candidates if p.is_file()), None)
if dependency is None:
    raise SystemExit("Set MT_XMLPULL_JAR to the resolved xmlpull dependency or the supplied APKEditor.jar")

with tempfile.TemporaryDirectory(prefix="mt-apk-editor-") as temporary:
    work = Path(temporary)
    api = work / "xmlpull-api.jar"
    with zipfile.ZipFile(dependency) as source, zipfile.ZipFile(api, "w") as target:
        for name in source.namelist():
            if name.startswith("org/xmlpull/"):
                target.writestr(name, source.read(name))
    source = (root / "antisplit-m/src/main/java/com/reandroid/arsc/chunk/xml/ResXmlPullParser.java").read_text()
    declarations = re.findall(r"@Override\s+public\s+([^\{]+)\{", source)
    shims = {
        "android/os/Build.java": "package android.os; public final class Build { public static class VERSION { public static final int SDK_INT=36; } public static class VERSION_CODES { public static final int GINGERBREAD=9,LOLLIPOP=21,N=24,O=26,P=28; } }",
        "android/text/TextUtils.java": "package android.text; public final class TextUtils { public static boolean isEmpty(CharSequence s) { return s == null || s.length() == 0; } }",
        "android/annotation/TargetApi.java": "package android.annotation; public @interface TargetApi { int value(); }",
        "android/util/Base64.java": "package android.util; public final class Base64 { public static String encodeToString(byte[] v,int f) { return java.util.Base64.getEncoder().encodeToString(v); } public static byte[] decode(String v,int f) { return java.util.Base64.getMimeDecoder().decode(v); } }",
        "android/content/res/XmlResourceParser.java": "package android.content.res; import java.io.*; import org.xmlpull.v1.*; public interface XmlResourceParser extends XmlPullParser, java.lang.AutoCloseable {\n" + "\n".join(d.strip() + ";" for d in declarations) + "\n}",
    }
    sources = []
    for relative, text in shims.items():
        path = work / relative
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(text)
        sources.append(path)
    sources += sorted((root / "antisplit-m/src/main/java").rglob("*.java"))
    sources += [root / "integration-tests/ApkEditorIntegrationTest.java"]
    sources += sorted((root / "apksig-android/src/main/java").rglob("*.java"))
    sources += sorted((root / "mt-crypto/src/main/java").rglob("*.java"))
    sources += [root / "integration-tests/ApkSigningIntegrationTest.java"]
    arguments = work / "sources.txt"
    arguments.write_text("\n".join('"' + str(p) + '"' for p in sources))
    classes = work / "classes"
    subprocess.run(["java", "com.sun.tools.javac.Main", "-cp", str(api), "-d", str(classes), "@" + str(arguments)], check=True)
    results = work / "results"
    results.mkdir()
    subprocess.run(["java", "-Xmx1600m", "-cp", str(classes) + os.pathsep + str(api), "ApkEditorIntegrationTest",
                    str(root / "apktool-android/src/main/assets/apktool/frameworks/sdk-36.apk"),
                    str(root / "app/src/main/assets"), str(results)], check=True, timeout=180)
    subprocess.run(["java", "-Xmx1600m", "-cp", str(classes) + os.pathsep + str(api), "ApkSigningIntegrationTest",
                    *[str(results / name) for name in ["optimized.apk", "refactored.apk", "protected.apk", "manifest-protected.apk", "killed.apk", "pairip.apk", "dex-protected.apk"]]], check=True, timeout=180)
