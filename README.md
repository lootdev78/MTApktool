# MTApktool

MTApktool is the integrated Android dual-panel explorer / Apktool workspace in this source tree. The project keeps the existing Apktool runtime, archive browser, split-package installer/converter and task process, while wiring the supplied AntiSplit-M, APKExtractor, APKCloner and MH TextEditor sources into the MTApktool host UI.

## Integrated modules

- `:antisplit-m` — upstream REAndroid-based AntiSplit-M merge engine used by APKS/APKM/XAPK/APKX -> APK and APKExtractor split merging.
- `:apkextractor` — installed-app extraction engine. The host entry is in the left navigation drawer; default output root is `/storage/emulated/0/apktool/apks` (shown in the UI as `/apktool/apks`). Single APKs produce `<package>.apk`; split apps can produce `<package>.apks` or a merged `<package>.apk`.
- `:apkcloner` — supplied APKCloner engine. It is exposed from `.apk` -> APK information -> `FUNKTIONEN` -> `APK klonen` and strips stale source signing metadata before the clone is re-signed by the MTApktool signing workflow.
- `:mh-editview` and `:mh-editor` — supplied MH TextEditor sources embedded as the MTApktool text/code editor. Their colors and day/night behavior follow the persisted MTApktool theme.
- Existing `:zipalign-android`, `:apksig-android`, `:apktool-android`, smali/antlr and Apktool modules remain part of the same project graph.

All module settings that remain meaningful inside the host are surfaced from the MTApktool Settings activities rather than separate standalone-module settings screens. Theme/language ownership stays with MTApktool; pane-aware output selection replaces standalone save-location settings.

## User-visible integration

### APK Extractor

Open the left drawer and choose **APK Extractor**. The screen can list user/system apps, search and sort them, extract base/split APKs, create APKS archives, merge installed splits with AntiSplit-M, and optionally export icons, manifest, DEX, resources and native libraries. Configure it from **Settings -> APK Extractor**.

Default output root:

```text
/storage/emulated/0/apktool/apks
```

### APK Cloner

Open an `.apk`, choose **FUNKTIONEN -> APK klonen**, enter the new package name and select same folder, left panel, right panel or a custom output path. The output APK is intentionally left unsigned after package/resource changes and can be signed with MTApktool's signing function/settings.

### AntiSplit-M / split containers

`.apks`, `.apkm`, `.xapk` and `.apkx` open the split-container workflow. Selected splits can be installed in one PackageInstaller session or merged to an APK through the integrated AntiSplit-M engine. Conversion output can target the source folder, left panel, right panel, `/apktool/projects/<project>`, or a custom path. Zipalign options are available in the same conversion workflow and in APK functions/settings.

### Settings and theme

The main settings page and category pages are real Activities. Integrated-module settings live under:

- **APK-Module** — AntiSplit-M and zipalign behavior.
- **APK Extractor** — output root, sorting, displayed fields, extraction functions, APKS vs merged APK defaults.
- **APK Cloner** — filename suffix and default output behavior.
- **Text Editor** — wrapping, autocomplete, line numbers, indent guides, syntax and selection-menu presentation.

Standalone module theme/color selectors are not duplicated: new module UI uses the MTApktool theme. MH TextEditor receives the same persisted light/dark/system mode through the host theme bridge.

## Build profiles

Shared platform values are defined in `gradle.properties`:

```text
mtapktool.compileSdk=36
mtapktool.minSdk=29
mtapktool.targetSdk=36
mtapktool.ndkVersion=29.0.14033849
```

Every Android module reads the shared compile SDK/min SDK/NDK values. The application also reads the shared target SDK. New source modules use the same selected Java profile as the existing modules.

### Java 17

```sh
./gradlew17 :app:assembleDebug
```

- Gradle 8.11.1
- AGP 8.10.1
- JDK 17 required

### Java 25

```sh
./gradlew25 :app:assembleDebug
```

- Gradle 9.1.0
- AGP 9.0.0
- JDK 25 required
- wrapper supplies the compatibility properties needed for the existing Kotlin Android plugin setup

The CI workflows verify both profiles and include the integrated source modules in their profile checks.

## Source and validation notes

See `MODULE_INTEGRATION_2026-09-15.md` for the integration map, retained upstream behavior and validation notes. Upstream licenses/readmes are retained inside the integrated module directories; see `THIRD_PARTY_MODULES.md` for provenance.
