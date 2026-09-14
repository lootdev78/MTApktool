# MTApktool

MTApktool combines the MTExplorer dual-pane file manager with the supplied Apktool-A Android port. Apktool remains connected as source/library modules; explorer UI and `.apk/.apks/.apkm/.xapk` handling live in `app/src`.

## Build profiles

MTApktool has two explicit, selectable source/compiler profiles. The selected profile is propagated through all included JVM and Android modules via `mtapktool.javaVersion`.

| Profile | Command | Gradle | AGP | Gradle JDK | Java/Kotlin target |
| --- | --- | --- | --- | --- | --- |
| Java 17 | `./gradlew :app:assembleDebug` | 8.11.1 | 8.10.1 | 17 | 17 |
| Pure Java 25 | `./gradlew25 :app:assembleDebug` | 9.1.0 | 9.0.0 | 25 | 25 |

The Java 25 wrapper refuses to run unless `JAVA_HOME`/`java` is JDK 25. `verifyJavaProfile` also checks the Java version of the actual Gradle runtime. Android Java modules use the profile for `sourceCompatibility`/`targetCompatibility`; JVM-only modules use it for the Java toolchain and `--release`; the app Kotlin sources use it for `jvmToolchain` and `JvmTarget`. The vendored Smali source build is wired to the same property as well.

Common Android baseline for both profiles:

- Package/applicationId: `io.github.lootdev78.mtapktool`
- compileSdk/targetSdk: **36**
- minSdk: **29**
- NDK: **29.0.14033849**
- ABI: `arm64-v8a`
- AndroidX Core: **1.18.0**
- Lifecycle Compose: **2.10.0**

A generated `gradle/gradle-daemon-jvm.properties` is intentionally ignored and both CI workflows remove any stale copy before their first project Gradle invocation. This prevents a committed `toolchainVersion=25` from hijacking the Java 17 workflow.

## Modules

`:app -> :apktool-android -> :brut.apktool:apktool-lib / :brut.apktool:apktool-cli / :apksig-android / :zipalign-android`, plus `:smali-android -> :antlr-runtime`

The original Apktool-A helper modules (`brut.j.*`, `smali-android`) and its framework/AAPT2 payloads remain connected.

## Explorer integration

- Tap `.apk` to open the dedicated APK information activity first; **Functions** then exposes decode, framework import, zipalign, share and related Apktool actions.
- `.apks`, `.apkm`, `.xapk` are inspected as split containers.
- Folders containing `apktool.yml` are recognized as Apktool projects.
- Inside an Apktool project the pane shows **Dieses Projekt kompilieren**.
- Build/decode/settings dialogs follow the Apktool-M screenshot ordering with the MTExplorer Material3 theme.
- Framework Manager, AAPT2 Manager, signing v1-v4, 1-4 top-level workers and 1-4 Apktool threads are included.
- AAPT2 is the only supported Android resource packager; the UI and custom-binary validation are AAPT2-only.

## GitHub Actions

`.github/workflows/build-debug.yml` is the Java 17 build and uploads `MTApktool-debug-java17`. `.github/workflows/build-debug-java25.yml` is a separate manually selectable **pure Java 25** build and uploads `MTApktool-debug-java25`. Both install Android SDK 36, build-tools 36.0.0 and NDK 29.0.14033849, verify the profile before compiling, run `:app:checkDebugAarMetadata`, and then build `:app:assembleDebug`.

## Smali parser toolchain

- The ANTLR Java runtime is vendored from the supplied Android ANTLR **3.5.3** source tree in `third_party/antlr-runtime` and built as `:antlr-runtime`.
- `DOTTreeGenerator.java` is intentionally omitted, matching the supplied Android `Android.bp`, so the app does not need StringTemplate at runtime.
- Smali grammar generation uses ANTLR **3.5.3** at build time.
- Smali lexer generation uses the stable JFlex **1.9.1** build dependency. The supplied JFlex tree identifies itself as `1.10.0-SNAPSHOT` and contains source/bootstrap files rather than a distributable generator JAR, so it is not packaged into the Android app.
- ANTLR/JFlex are build-time parser generators only; generated Smali parser/lexer classes and the local ANTLR runtime are what ship in the app.

## File manager fixes included

- Settings in the navigation drawer opens the real Settings activity and its section activities.
- Share uses `FileProvider` content URIs instead of `file://` URIs.
- Open-with, ZIP compression, symbolic-link creation and persistent bookmarks are wired into the context menu.
- Cross-filesystem moves fall back to copy/delete, and copy/move guards against recursively transferring a directory into itself.
- Decode/build dialogs now expose previously unreachable Apktool flags such as only-manifest, resource resolve mode, ignore-raw-values, no-assets, force and no-crunch.
- AAPT2 selections are validated before saving; custom binaries must execute and identify as AAPT2.
- APK signing rejects an all-disabled scheme configuration and v4 signatures use the conventional `<output.apk>.idsig` path.

### Apktool jobs
Heavy Apktool operations are executed by the foreground `ApktoolJobService` in the dedicated `:apktool` process. Live output can be hidden without cancelling a task and reopened from the right-side Task panel. A plain APK click offers both decode and framework installation.

### Read-only archive browsing and extraction
Supported ZIP/JAR/APK, 7z, TAR-family and RAR archives can be opened directly in either explorer pane. The archive is mounted through a private cache workspace and is deliberately read-only: files can be viewed/opened/installed from there or copied/extracted out to a normal filesystem pane, while rename/delete/move/create operations into the archive are blocked. Leaving the archive discards the temporary workspace and never rewrites the source archive. The Extract dialog supports the current pane, the opposite pane, a relative destination folder, optional source deletion, and passwords where supported.


## Explorer / package workflow update (2026-09-14)

- Settings now open as real activities rather than the old full-screen settings dialog.
- APK taps open an APK information activity; **VIEW** opens the APK archive in the current explorer pane.
- `.apks`, `.apkm`, `.xapk` and `.apkx` open a split-package screen with one-session install, extraction, decode and Split→APK controls.
- Decode/build and Split→APK output can target same folder, left pane, right pane, app defaults or a custom path; decoded sources use `/apktool/projects/<app-name>/` as the explicit app-default destination.
- `zipalign-android` is wired into APK functions and Split→APK output.
- ZIP/JAR/APK, 7z, TAR-family and read-only RAR browsing/extraction are supported.
- Added persisted hidden/filter/sort options, per-folder sort overrides with management UI, and SAF Document Tree locations.
- Both navigation drawers use finger-bound drag progress with velocity/threshold settling; the right task drawer keeps active tasks and clears/removes terminal tasks only.
- Explorer text-file taps use external/open-with handling; no new internal editor workflow was added.

See `IMPLEMENTATION_NOTES_2026-09-14.md` for the exact AntiSplit-M source status and validation notes.
