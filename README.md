# MTApktool

MTApktool combines the MTExplorer dual-pane file manager with the supplied Apktool-A Android port. Apktool-A stays as source/library modules; APK/APKS/APKM/XAPK handling, project detection, Material3 dialogs and job UI live in `app/src`.

## Android / Gradle baseline

The Android build baseline is intentionally taken from the supplied **Apktool-A.zip**, not from the newer MTExplorer Gradle setup. This matters because the vendored Apktool Android module build scripts use AGP 8 source-set APIs and are not compatible with AGP 9.x.

- App: **MTApktool**
- Package / applicationId: **`io.github.lootdev78.mtapktool`**
- `compileSdk = 36`
- `targetSdk = 36`
- `minSdk = 29`
- `ndkVersion = "29.0.14033849"` where an Android/native module needs the NDK
- Bundled native ABI: `arm64-v8a`
- JDK used by Gradle: 17
- Apktool/smali Java compatibility: Java 8
- MTApktool app Kotlin/Java target: JVM 17
- Gradle wrapper: **8.11.1**
- Android Gradle Plugin: **8.10.1**
- Kotlin / Compose plugin: 2.4.10

AGP 8 does not provide AGP 9's built-in Kotlin support, therefore the MTExplorer app applies `org.jetbrains.kotlin.android` explicitly. The Apktool-A Gradle wrapper and Android-library build files are kept on their known-good AGP 8.10.1 / Gradle 8.11.1 baseline.

## Source modules

`:app` depends on `:apktool-android`. That adapter then connects the vendored source modules rather than copying Apktool into the explorer app:

- `:brut.apktool:apktool-lib`
- `:brut.apktool:apktool-cli`
- `:brut.j.common`, `:brut.j.util`, `:brut.j.dir`, `:brut.j.xml`, `:brut.j.yaml`
- `:smali-android`
- `:apksig-android`
- `:zipalign-android`

The original Apktool-A Android runtime, framework assets and bundled AAPT2 binaries are retained under the module/source tree. Split-container handling from the explorer side remains in `app/src`.

## Explorer and Apktool-M-style UI

- Tap or context action on `.apk` opens the Apktool decode dialog.
- `.apks`, `.apkm` and `.xapk` are inspected as split containers with base/universal selection and an all-splits path.
- Directories containing `apktool.yml` are recognized automatically as Apktool projects.
- Inside such a project MTApktool shows **Dieses Projekt kompilieren** above the file list and exposes the build action in the file-manager workflow.
- Decode/build dialog ordering follows the supplied Apktool M screenshots while using MTApktool's Material3 theme.
- Decode quick settings include resources, DEX selection, debug info, registers/locals, directory preservation, broken resources, split traces, property removal and threads.
- Build dialog includes AAPT2, framework, signature, build settings, threads and output. **AAPT1 is intentionally not exposed.**
- Signature options expose v1/v2/v3/v4.
- The full **Erstellen & Dekodieren** settings screen follows the screenshot ordering and links to Framework Manager, AAPT2 Manager, signature, paths/jobs and runtime information.

Default decode project root: `/storage/emulated/0/apktool/projects`  
Default build output root: `/storage/emulated/0/apktool/output`

## Jobs

`ApktoolJobService` supports 1-4 concurrent top-level jobs. Each job has an ID, queue/running/final state and its own log; a job can be stopped individually or all active jobs can be stopped. AAPT2 child processes are interrupt-aware. Framework mutation and provisioning are synchronized so multiple decode/build jobs do not corrupt shared state. Apktool's internal worker count is also bounded to avoid multiplying four top-level runners into uncontrolled CPU usage.

## Framework / AAPT2

Framework Manager can list, import, select, delete and reset installed frameworks, including tagged framework files. The supplied SDK33/34/35/36 framework payloads remain connected to the runtime. AAPT2 Manager can select the supplied variants or a custom AAPT2 executable. AAPT1 is not part of the MTApktool UI.

## GitHub Actions

`.github/workflows/build-debug.yml` builds `:app:assembleDebug` on pushes to `main` or `master` and on manual dispatch. It installs JDK 17, Android SDK 36, build-tools 36.0.0 and NDK `29.0.14033849`, uses the repository's Gradle **8.11.1** wrapper, and uploads `app/build/outputs/apk/debug/*.apk` as `MTApktool-debug`.

## Local validation note

This execution environment has no usable downloaded Gradle distribution and cannot resolve `services.gradle.org`, so a full Android Gradle build cannot be completed here. The project/module graph, source roots, Android/resource XML, version catalog, wrapper/configuration files and bundled runtime payload paths are validated statically. The included GitHub Actions workflow is the intended real build check with network access.

## Licenses

MTExplorer-derived application code is covered by `LICENSE`. Apktool-derived code and notices are covered by `APKTOOL_LICENSE.md` and the vendored upstream notices. AntiSplit-M-derived split-container logic is covered by `ANTISPLIT_LICENSE.md`.
