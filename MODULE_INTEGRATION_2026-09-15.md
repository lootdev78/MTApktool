# Module integration - 2026-09-15

This update integrates the supplied AntiSplit-M redesign, APKExtractor, APKCloner and MH TextEditor source trees into the existing MTApktool project rather than shipping them as unrelated standalone apps.

## Wiring

- `settings.gradle.kts` includes `:antisplit-m`, `:apkextractor`, `:apkcloner`, `:mh-editview`, and `:mh-editor`.
- `app` depends on all host-facing modules.
- New Android library modules read `mtapktool.javaVersion`, `mtapktool.compileSdk`, `mtapktool.minSdk`, and `mtapktool.ndkVersion` from the same root profile used by the existing application/modules.
- Java 17 and Java 25 wrappers/CI validate the same integrated module graph.

## AntiSplit-M

The real supplied REAndroid-based merge source is under `antisplit-m/src/main/java/com/reandroid` and is exposed to MTApktool through `AntiSplitEngine`.

Host uses:

- APKS/APKM/XAPK/APKX -> APK conversion.
- installed split-app merge from APK Extractor.
- device split selection, optional/config/feature split handling, compression, force-merge, split-metadata cleanup and stale META-INF cleanup.

Standalone AntiSplit theme/language/update UI is deliberately not duplicated: MTApktool owns app theme/language lifecycle. Standalone file-save mode is replaced by MTApktool's source/left/right/app-default/custom output selection.

## APK Extractor

`ApkExtractorEngine` is a host library. `ApkExtractorActivity` is reachable from the left explorer navigation and uses the MTApktool Compose theme.

Default output root is `/storage/emulated/0/apktool/apks`. The extractor supports:

- base APK extraction;
- APKS archive generation for split apps;
- real AntiSplit-M merge to one APK;
- individual split extraction;
- icon extraction;
- AndroidManifest.xml extraction;
- DEX/resource/native-library archive extraction;
- user/system app filtering, search and sorting.

The related options are under the MTApktool `APK Extractor` settings Activity.

## APK Cloner

The supplied `ApkCloner` engine is kept in `:apkcloner` with its required bundled parser/ZIP libraries and shared Guava/xmlpull dependencies. MTApktool exposes it only for `.apk` files from APK information -> `FUNKTIONEN` -> `APK klonen`.

The host dialog supports a new package name and source/left/right/custom output targets. Source APK v1 signing metadata (`META-INF/MANIFEST.MF`, `.SF`, `.RSA`, `.DSA`, `.EC`) is removed from the modified clone so stale signatures are not carried forward. The result is ready for MTApktool's normal signing pipeline.

## MH TextEditor

The supplied edit view and editor sources are built as `:mh-editview` and `:mh-editor`. Explorer text/code opens the embedded editor. The editor's XML/legacy AppCompat palette has been mapped to the same MTApktool light/dark colors, and host theme mode is bridged so forced light/dark/system behavior is consistent.

Editor settings are hosted under `Settings -> Text Editor`.

## Static validation performed in this environment

The final packaging pass checks:

- XML well-formedness for manifests/resources;
- no merge-conflict markers;
- integrated module declarations and app dependencies;
- shared SDK/NDK/Java-profile declarations for new Android modules;
- Java 17/25 wrapper shell syntax and expected distributions;
- required Activity declarations and left-navigation/APK-function wiring;
- duplicate top-level Java class names across the integrated modules;
- final ZIP integrity.

A full Android Gradle compile cannot be executed in this container because it currently provides JDK 21, no Android SDK/android.jar, and no usable cached Gradle distribution for the required Java 17/25 profiles. The checked-in CI workflows are configured to build both supported profiles in an Android/JDK-equipped environment.
