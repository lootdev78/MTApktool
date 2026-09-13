# MTApktool

MTApktool combines the MTExplorer dual-pane file manager with the supplied Apktool-A Android port. Apktool remains connected as source/library modules; explorer UI and `.apk/.apks/.apkm/.xapk` handling live in `app/src`.

## Build baseline

- Package/applicationId: `io.github.lootdev78.mtapktool`
- Android Gradle Plugin: **8.10.1**
- Gradle wrapper: **8.11.1**
- JDK: **17**
- compileSdk/targetSdk: **36**
- minSdk: **29**
- NDK: **29.0.14033849**
- ABI: `arm64-v8a`
- AndroidX Core: **1.18.0** (1.19.0 requires SDK 37 / AGP 9.1+)
- Lifecycle Compose: **2.10.0** (2.11.0 requires SDK 37 / AGP 9.1+)

## Modules

`:app -> :apktool-android -> :brut.apktool:apktool-lib / :brut.apktool:apktool-cli / :apksig-android / :zipalign-android`, plus `:smali-android -> :antlr-runtime`

The original Apktool-A helper modules (`brut.j.*`, `smali-android`) and its framework/AAPT2 payloads remain connected.

## Explorer integration

- Tap `.apk` to open Apktool decode options.
- `.apks`, `.apkm`, `.xapk` are inspected as split containers.
- Folders containing `apktool.yml` are recognized as Apktool projects.
- Inside an Apktool project the pane shows **Dieses Projekt kompilieren**.
- Build/decode/settings dialogs follow the Apktool-M screenshot ordering with the MTExplorer Material3 theme.
- Framework Manager, AAPT2 Manager, signing v1-v4, 1-4 top-level workers and 1-4 Apktool threads are included.
- AAPT1 is intentionally not exposed.

## GitHub Actions

`.github/workflows/build-debug.yml` installs Android SDK 36, build-tools 36.0.0 and NDK 29.0.14033849, checks the SDK-36 compatible AndroidX pins, runs `:app:checkDebugAarMetadata`, builds `:app:assembleDebug`, and uploads the debug APK.

## Smali parser toolchain

- The ANTLR Java runtime is vendored from the supplied Android ANTLR **3.5.3** source tree in `third_party/antlr-runtime` and built as `:antlr-runtime`.
- `DOTTreeGenerator.java` is intentionally omitted, matching the supplied Android `Android.bp`, so the app does not need StringTemplate at runtime.
- Smali grammar generation uses ANTLR **3.5.3** at build time.
- Smali lexer generation uses the stable JFlex **1.9.1** build dependency. The supplied JFlex tree identifies itself as `1.10.0-SNAPSHOT` and contains source/bootstrap files rather than a distributable generator JAR, so it is not packaged into the Android app.
- ANTLR/JFlex are build-time parser generators only; generated Smali parser/lexer classes and the local ANTLR runtime are what ship in the app.

## File manager fixes included

- Settings in the navigation drawer now opens the Apktool settings UI.
- Share uses `FileProvider` content URIs instead of `file://` URIs.
- Open-with, ZIP compression, symbolic-link creation and persistent bookmarks are wired into the context menu.
- Cross-filesystem moves fall back to copy/delete, and copy/move guards against recursively transferring a directory into itself.
- Decode/build dialogs now expose previously unreachable Apktool flags such as only-manifest, resource resolve mode, ignore-raw-values, no-assets, force and no-crunch.
- AAPT2 selections are validated before saving; custom binaries must execute and identify as AAPT2.
- APK signing rejects an all-disabled scheme configuration and v4 signatures use the conventional `<output.apk>.idsig` path.
