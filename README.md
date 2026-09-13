# MTApktool

MTApktool combines the MTExplorer dual-pane file manager with the supplied Apktool-A Android source port. Apktool is not started as an external desktop process: the app calls the vendored Java sources in-process through `ApktoolJobService -> ApktoolCommandRunner -> brut.androlib.ApkDecoder.decode()`.

## Build baseline

- Package/applicationId: `io.github.lootdev78.mtapktool`
- Android Gradle Plugin: **8.10.1**
- Gradle wrapper: **8.11.1**
- JDK: **17**
- compileSdk/targetSdk: **36**
- minSdk: **29**
- NDK: **29.0.14033849**
- ABI: `arm64-v8a`
- AndroidX Core: **1.18.0**
- Lifecycle Compose: **2.10.0**

The AndroidX pins keep the project on SDK 36 / AGP 8.10.1 instead of pulling dependencies that require API 37 and AGP 9.1+.

## Real Apktool source runtime

The source-module chain is:

`app -> apktool-android -> brut.apktool:apktool-lib / brut.apktool:apktool-cli / apksig-android / zipalign-android`

The original Apktool-A helper modules (`brut.j.*`, `smali-android`) and framework/AAPT2 payloads remain connected. Normal decode uses `new ApkDecoder(apk, config).decode(output)` directly from the vendored Apktool sources.

Decode provisioning is intentionally separate from build provisioning. **APK decoding does not require the native AAPT2 payload.** AAPT2 is provisioned only for build/repack commands. This prevents an AAPT2 extraction/ABI problem from blocking APK decompilation.

The Settings -> Runtime page performs a real self-test of the embedded source port, provisions the decode framework files and executes the in-process runner's `apktool --version` command.

## Decode workflow

Tapping `.apk`, `.apks`, `.apkm` or `.xapk` in either explorer pane opens the Apktool decode dialog. `.apks/.apkm/.xapk` containers are inspected for base/universal and split APK entries.

The decode dialog includes:

- resources, `classes*.dex`, all DEX and `.nomedia`
- framework selection
- additional resource packages with the four Apktool-M-style choices:
  - `Nicht dekompilieren`
  - `Dekompilieren in das Hauptverzeichnis`
  - `Dekompilieren in ein separates Verzeichnis`
  - `Versuche, Pakete zusammenzuführen`
- the additional-resource choices are disabled when **Ressourcen dekompilieren** is disabled or manifest-only mode is active
- editable output directory
- Android system folder picker (`OpenDocumentTree`) with the system's create-folder action
- automatic creation/writeability validation of a typed output directory
- runtime readiness/error information before the job is queued
- quick decode settings and 1-4 Apktool threads

Additional resource packages are implemented in the vendored Apktool source (`Config.DecodeAdditionalResources` + `ResDecoder`), not as UI-only placeholders.

## Explorer / project workflow

- Folders containing `apktool.yml` are recognized automatically as Apktool projects.
- Inside such a project the pane shows **Dieses Projekt kompilieren**.
- Build uses AAPT2 only; AAPT1 is intentionally not exposed.
- Framework Manager, AAPT2 Manager, signing v1-v4, 1-4 top-level jobs and 1-4 Apktool threads are included.
- The left navigation **Settings** item opens the real **Erstellen & Dekodieren** settings screen.
- The left navigation also exposes **Apktool Jobs**.

## Background jobs and notifications

Apktool work runs in `ApktoolJobService`, not in the Activity. The service is a foreground `specialUse` service and holds a partial wake lock while jobs are active. Closing/swiping the UI does not deliberately cancel active jobs. The service uses `START_REDELIVER_INTENT`, per-job futures and cooperative interruption/cancellation.

The Activity is `singleTask`/`alwaysRetainTaskState`; explorer paths and active pane are kept through `SavedStateHandle`, and job snapshots are persisted so reopening the UI does not reset the explorer to the storage root or lose the last job state.

Android notification channels are created for:

- ongoing Apktool job/progress notifications
- completed/failed job notifications

POST_NOTIFICATIONS is requested on Android 13+, and notification permission/state is visible in Settings. Completion notifications are enabled by default even while MTApktool is open unless the user enables suppression.

## Storage

MTApktool requests all-files access because the file manager and Apktool operate on raw `java.io.File` paths. The decode output picker uses Android's folder chooser, persists its read/write URI grant, and resolves external-storage tree URIs back to raw storage paths needed by Apktool.

Default project root: `/storage/emulated/0/apktool/projects`  
Default build output root: `/storage/emulated/0/apktool/output`

## GitHub Actions

`.github/workflows/build-debug.yml` installs Android SDK 36, build-tools 36.0.0 and NDK 29.0.14033849, checks the SDK-36 dependency pins and source-module/runtime wiring, runs `:app:checkDebugAarMetadata`, builds `:app:assembleDebug`, and uploads the debug APK.

## Validation note

This execution container has no usable Gradle distribution/network access for a complete Android build. Source/module paths, XML/TOML/YAML, runtime wiring and parser-level Java/Kotlin checks are performed locally; GitHub Actions remains the authoritative full Android compile/package test.
