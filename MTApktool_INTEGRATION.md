# MTApktool integration notes

MTApktool combines the MTExplorer dual-pane file manager with the supplied Apktool-A Android port. The Apktool-A activity/UI is intentionally not embedded; its toolchain, Android-adapted Apktool sources, smali/baksmali, apksig, Java zipalign, frameworks and AAPT2 payloads are integrated behind MTExplorer's existing Compose file-manager workflow.

## Android build baseline

- App name/root project: **MTApktool**
- Package / applicationId: **`io.github.lootdev78.mtapktool`**
- `compileSdk`: **36** for every Android module
- `targetSdk`: **36**
- `minSdk`: **26** (preserves MTExplorer's baseline)
- `ndkVersion`: **29.0.14033849** across Android modules
- packaged native ABI: **arm64-v8a**
- Gradle wrapper inherited from MTExplorer: 9.5.0
- Android Gradle Plugin inherited from MTExplorer: 9.3.1

The supplied AAPT2 files are prebuilt ARM64 executables. They were not rebuilt with NDK 29 because Apktool-A did not contain AAPT2 source. NDK 29 is pinned for native source that can be built from this project. Default zipalign is the vendored Java implementation, so a normal build does not require CMake.

## File-manager workflow

- Tap `.apk` -> Apktool decode dialog.
- Tap `.apks`, `.xapk` or `.apkm` -> APK-set decode dialog.
  - Auto mode prefers `universal.apk`, `base.apk`, `base-master.apk`, then a likely base APK.
  - Optional **all splits** mode decodes every contained APK into a separate project directory.
- Long-press APK/APK-set -> **Apktool Decode** in the file context menu.
- Long-press a directory containing `apktool.yml` -> **APK kompilieren**.
- While inside an Apktool project directory, the header marks **Apktool project** and the top overflow menu exposes **APK kompilieren (Projekt)**.
- Top overflow menu -> **Apktool Jobs**, **Apktool Settings**, **Apktool CLI**.

Default decode project root is `/storage/emulated/0/apktool/projects` (shown in UI as `/apktool/projects`). Build output defaults to `/storage/emulated/0/apktool/output`.

## Apktool settings

Global settings use MT/Apktool-M style subdialogs:

- **Framework Manager**: default / SDK36 / SDK35 / SDK34 / SDK33, installed framework list, Android file picker and tagged install;
- **AAPT2 Manager**: auto / SDK36 alias / SDK35 / SDK33 / legacy AAPT2 / custom binary; AAPT1 is intentionally absent;
- persistent decode defaults;
- persistent compile defaults;
- paths and parallel top-level jobs (1-4);
- runtime/toolchain information.

Projects and build-output roots are both configurable.

SDK36 is provisioned both as `1-sdk36.apk` and `1.apk`, so it remains the default framework. `sdk36` AAPT2 is an alias in the supplied port: on >=16 KiB page-size runtimes it selects the 64 KiB-aligned SDK33 binary; otherwise it selects the newer supplied SDK35 binary.

## Job runner

`ApktoolJobService` replaces Apktool-A's single-thread/single-current-job service.

- up to 4 top-level jobs;
- queued/running/succeeded/failed/cancelled state per job;
- unique job IDs;
- individual Stop and Stop all;
- one foreground-service summary notification;
- per-job log files under `/storage/emulated/0/apktool/logs`;
- configurable worker count while the service is alive;
- `Future.cancel(true)` plus cooperative interrupt checks;
- AAPT2 subprocesses are destroyed through the port's interrupt-aware `OS.exec` path;
- provisioning is serialized to avoid concurrent framework/AAPT2/debug-key copy races;
- framework install/clean operations take an exclusive lock while decode/build/list operations take a shared lock;
- process-global Java logging is filtered per job thread;
- `apktool-original` is serialized because it temporarily replaces process-global `System.out/System.err`.

To avoid accidental 16-thread oversubscription, the default Apktool internal worker budget is divided across the configured top-level runners: 1 runner -> 4 internal jobs, 2 -> 2, 3-4 -> 1. Explicit `-j/--jobs` is limited to 1-4 on Android.

## Decode/build options

The Compose dialogs expose the common MT/Apktool-M style path:

- Decode: force, no sources, no debug info, no resources, only manifest, resource resolve mode, match-original, keep broken resources, ignore raw values, no assets, framework tag, output path.
- Build: force, debuggable, copy-original, no-crunch, network-security-config, no-apk, AAPT2 variant/custom AAPT2, output APK, optional 16 KiB-aware zipalign and v1/v2/v3 debug signing.
- CLI: full integrated runner commands plus `apktool-original ...` fallback for upstream command-line options not represented as switches.

The integrated Java runner mirrors the supplied upstream Apktool 3.x decode/build/framework options and also provides `zipalign`, `apksigner`, and APK-set decode helpers without `System.exit()`.

## Static validation performed without Gradle

- Android/resource XML parses successfully.
- Version catalog TOML parses successfully.
- Required Apktool, smali, apksig and zipalign sources are present.
- Bundled SDK33/34/35/36 framework APK ZIPs pass CRC validation.
- AAPT2 payloads are ARM64 ELF files; SDK33 has `0x10000` LOAD alignment, while the supplied SDK35 payload contains 4 KiB LOAD alignment and is therefore not selected automatically on a large-page runtime.
- All `zipalign-android` Java sources compile with standalone `javac`.
- A synthetic APK was aligned and verified successfully with 16 KiB shared-library page alignment.
- Modified Kotlin/Java sources were syntax-checked with local compilers; unresolved Android/Compose/dependency symbols are expected without the Gradle Android classpath, and no parser/syntax errors were found.

## Device/build validation still required

This environment intentionally did not run Gradle. Before treating the source as a release build, run the actual Android build with SDK 36 and then test on real ARM64 hardware:

1. first launch + all-files permission + notification permission;
2. provision bundled framework SDK36;
3. decode a normal APK;
4. decode APKS base and all-splits modes;
5. run two to four simultaneous decode jobs and cancel queued/running jobs individually;
6. build -> 16 KiB zipalign -> v1/v2/v3 sign -> verify -> install;
7. test framework tags SDK33/34/35/36;
8. test default AAPT2 on a 4 KiB device and a 16 KiB page-size ARM64 device;
9. test process/background/notification behavior under Android 14-16 foreground-service restrictions.

Release signing is optional in the Gradle file: if `app/release.jks` is absent, release is not forced to reference a nonexistent keystore. R8/resource shrinking is disabled for the initial integrated release until the reflection-sensitive Apktool/smali/ANTLR paths have been device-tested.
