# MTApktool

MTApktool is an Android dual-pane file manager based on MTExplorer with the supplied Apktool-A Android port integrated as reusable source/library modules. Apktool operations are launched directly from files and automatically detected Apktool project folders; the original Apktool-A activity/UI is not embedded.

## Android baseline

- App: **MTApktool**
- Package / applicationId: **`io.github.lootdev78.mtapktool`**
- `compileSdk = 36`
- `targetSdk = 36`
- `minSdk = 26`
- `ndkVersion = "29.0.14033849"`
- ABI for bundled AAPT2: `arm64-v8a`
- Java bytecode/source level for Android modules: 17
- Gradle wrapper: 9.5.0
- Android Gradle Plugin: 9.3.1
- Kotlin/Compose plugin: 2.4.10

The Apktool-A runtime lives in `:apktool-android`. Apktool/smali/apksig/zipalign supporting sources remain modular and are referenced by Gradle projects; they are not copied into MTExplorer's `app/src`.

## Explorer integration

- Tap `.apk` -> **Apktool decode** dialog.
- Tap `.apks`, `.apkm`, `.xapk` -> split-container decode dialog with automatic base/universal selection or **all splits**.
- Long-press APK/container -> **Apktool decodieren**.
- A folder containing `apktool.yml` is automatically recognized as an Apktool project.
- Long-press an Apktool project -> **APK kompilieren**.
- When browsing inside an Apktool project, the header identifies it and the overflow menu exposes **APK kompilieren (Projekt)**.

Default projects: `/storage/emulated/0/apktool/projects`  
Default build output: `/storage/emulated/0/apktool/output`

## Apktool dialogs

### Decode

Framework tag, project output, force, no sources, no debug info, no resources, only manifest, resource resolve mode, match-original, keep broken resources, ignore raw values and no-assets are exposed. Decode defaults are persistent.

### Compile

Output APK, AAPT2 selection, custom AAPT2 path, zipalign, v1/v2/v3 debug signing, force, debuggable, copy-original, no-crunch, network-security-config and `--no-apk` are exposed. Build defaults are persistent.

### Settings

The settings hub provides separate dialogs for:

- **Framework Manager** – active framework tag, installed frameworks, APK picker/install, bundled SDK33/34/35/36 provisioning.
- **AAPT2 Manager** – automatic, SDK36-compatible, SDK35, SDK33 (16K-safe), legacy bundled AAPT2, or custom AAPT2. **No AAPT1 option is exposed.**
- **Decode defaults**.
- **Compile defaults**.
- **Paths & Jobs** – projects/output paths and 1–4 parallel runners.
- **Runtime** – Apktool version, SDK/NDK/ABI, runtime page size and toolchain paths.

## Jobs

`ApktoolJobService` supports up to four concurrent top-level jobs with unique IDs, queue/running/final state, per-job logs, individual Stop and Stop all. AAPT2 child processes are interrupt-aware and killed when necessary. Framework mutations are serialized against concurrent decode/build jobs, while normal decode/build jobs can run in parallel.

To cap CPU oversubscription, Apktool's internal worker budget is divided across the selected top-level runners.

## Split archives

`.apks`, `.apkm` and `.xapk` remain an MTExplorer integration concern. Container inspection follows AntiSplit-M-compatible base/config naming heuristics, with additional bundletool/APKM/XAPK preferences for `universal.apk`, `base.apk` and `base-master.apk`. The selected APK(s) are extracted to a temporary toolchain directory and decoded through the Apktool module.

## Building

This source tree expects Android SDK 36 and NDK `29.0.14033849`. The current execution environment has no installed Gradle distribution and no network access, so a full Android Gradle build cannot be run here. Open the source in an Android development environment with the required SDK/NDK and let the included wrapper resolve Gradle 9.5.0.

## Licenses

- MTExplorer-derived application code: see `LICENSE`.
- Apktool-derived code: see `APKTOOL_LICENSE.md` and upstream notices in the vendored sources.
- AntiSplit-M-derived split naming logic: see `ANTISPLIT_LICENSE.md`.
