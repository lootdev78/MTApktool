# MTApktool functional Apktool overlay

Apply this ZIP over the repository root and overwrite existing files.

This overlay keeps the Apktool-A toolchain baseline:
- Gradle 8.11.1
- AGP 8.10.1
- JDK 17
- compileSdk / targetSdk 36
- NDK 29.0.14033849
- AndroidX core 1.18.0 and lifecycle Compose 2.10.0 (SDK 36 compatible)

Functional changes:
- APK/APKS/APKM/XAPK clicks route to the Apktool decode dialog.
- Additional-resource options are disabled unless resource decode is enabled.
- Additional-resource modes: none, main project, separate projects, merge.
- Decode output is editable and has a filesystem folder picker with folder creation.
- Left drawer Settings opens the complete Apktool settings screen.
- Foreground Apktool jobs show aggregate + per-job notifications with STOP action.
- Up to 4 jobs continue independently while the Activity is in the background.
- Job history/status is persisted; START_REDELIVER_INTENT is used after process loss.
- MainActivity uses singleTask so tapping a job notification reuses the existing Activity.
- Explorer pane paths and active pane use SavedStateHandle.
- New launcher and notification icons.

Source port status:
- app -> apktool-android -> apktool-lib/apktool-cli is unchanged.
- apktool-lib -> smali-android remains source based.
- smali-android compiles smali, baksmali, dexlib2 and util from third_party/smali-src.
- JFlex/ANTLR remain build-time generators/runtime dependencies; the uploaded JFlex/ANTLR trees are useful for a later fully vendored/offline generator toolchain, but are intentionally not mixed into this functional overlay because they use different generator versions.

Build:
`./gradlew :app:assembleDebug --stacktrace --no-daemon`
