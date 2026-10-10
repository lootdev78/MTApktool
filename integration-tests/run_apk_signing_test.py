#!/usr/bin/env python3
"""JVM apksig integration check with tiny Android API shims; no Android device validation."""
from pathlib import Path
import subprocess
import tempfile

root = Path(__file__).resolve().parents[1]
with tempfile.TemporaryDirectory(prefix='mt-apksig-') as temporary:
    output = Path(temporary)
    shims = {
        'android/text/TextUtils.java': 'package android.text; public final class TextUtils { public static boolean isEmpty(CharSequence s) { return s == null || s.length() == 0; } }',
        'android/os/Build.java': 'package android.os; public final class Build { public static class VERSION { public static final int SDK_INT=36; } public static class VERSION_CODES { public static final int GINGERBREAD=9,LOLLIPOP=21,N=24,O=26,P=28; } }',
        'android/annotation/TargetApi.java': 'package android.annotation; public @interface TargetApi { int value(); }',
    }
    sources = []
    for relative, text in shims.items():
        path = output / relative
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(text)
        sources.append(path)
    sources += sorted((root / 'apksig-android/src/main/java').rglob('*.java'))
    sources += sorted((root / 'mt-crypto/src/main/java').rglob('*.java'))
    sources += [root / 'integration-tests/ApkSigningIntegrationTest.java']
    subprocess.run(['java', 'com.sun.tools.javac.Main', '-d', temporary, *map(str, sources)], check=True)
    subprocess.run(['java', '-cp', temporary, 'ApkSigningIntegrationTest', str(root / 'apktool-android/src/main/assets/apktool/frameworks/sdk-36.apk')], check=True, timeout=60)
