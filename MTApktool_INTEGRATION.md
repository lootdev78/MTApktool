# MTApktool integration notes

MTApktool keeps MTExplorer as the application/file-manager layer and loads the supplied Apktool-A port as source modules. The old Apktool-A activity is not embedded; its runtime/toolchain is called from the explorer's Material3 APK/project dialogs.

## Known-good build baseline

The integration deliberately follows Apktool-A's Android/Gradle baseline:

- root project / app: **MTApktool**
- package / applicationId: **`io.github.lootdev78.mtapktool`**
- `compileSdk`: **36** on Android modules
- `targetSdk`: **36** for the app
- `minSdk`: **29**, matching Apktool-A's Android libraries
- NDK: **29.0.14033849** on modules that use/package native tooling
- Gradle wrapper: **8.11.1**
- Android Gradle Plugin: **8.10.1**
- Gradle JDK: **17**
- Apktool/smali Java compatibility: **8**
- app Kotlin/Java JVM target: **17**

The earlier AGP 9.3.1 / Gradle 9.5 integration is intentionally removed. `brut.apktool/apktool-cli/build.gradle.kts` and the other vendored Apktool Android library scripts are restored to the Apktool-A-compatible AGP 8 form. Under AGP 9 those scripts can fail while configuring Android source sets with a `DefaultAndroidLibrarySourceSet_Decorated` / `AndroidLibrarySourceSet` cast error.

MTExplorer still uses its Kotlin/Compose dependencies, but because AGP 8 does not embed Kotlin, `:app` explicitly applies `org.jetbrains.kotlin.android` in addition to the Compose compiler plugin.

## Project graph

`settings.gradle.kts` includes:

- `:app`
- `:apktool-android`
- `:apksig-android`
- `:zipalign-android`
- `:smali-android`
- `:brut.j.common`, `:brut.j.util`, `:brut.j.dir`, `:brut.j.xml`, `:brut.j.yaml`
- `:brut.apktool:apktool-lib`
- `:brut.apktool:apktool-cli`

`:app -> :apktool-android -> Apktool/apksig/zipalign` keeps Apktool-A reusable as a module. `apktool-lib` remains connected to the vendored helper and smali sources through its original build scripts. Framework APK assets and AAPT2 JNI payloads remain in the module tree.

## File-manager workflow

- `.apk` -> Apktool decode dialog.
- `.apks`, `.apkm`, `.xapk` -> explorer-side split inspection, preferred base/universal selection, optional all-splits decode.
- A folder with `apktool.yml` -> automatically recognized project.
- When the user is inside a project, a **Dieses Projekt kompilieren** row is displayed above the ordinary directory contents.
- Project build dialog exposes only AAPT2, framework, signature, quick settings and output; AAPT1 is intentionally omitted.
- Explorer overflow keeps global **Erstellen & Dekodieren**, jobs and CLI entry points.

Default paths remain `/storage/emulated/0/apktool/projects`, `/storage/emulated/0/apktool/output`, and `/storage/emulated/0/apktool/logs`.

## Apktool-M-style Material3 dialogs

The screenshot layout is followed at the interaction/ordering level while retaining MTApktool's Material3 theme.

Decode dialog ordering: resources -> `Klassen*.dex` -> all DEX -> `.nomedia` -> framework -> additional-resource/split handling -> output -> `EINSTELLUNGEN / ABBRECHEN / OK`.

Decode quick settings: debug information -> registers instead of locals -> preserve directory structure -> `APKTOOL_DUMMY` status -> keep broken resources -> remove split traces -> remove `<property>` -> `THREADS / ABBRECHEN / SPEICHERN`.

Project build dialog: AAPT2 -> framework -> signature -> output -> `EINSTELLUNGEN / ABBRECHEN / OK`. Build quick settings contain debuggable, network-security configuration, delete `build`, and original/copy checksums behavior. Signature scheme dialog exposes v1/v2/v3/v4 and optional persistence. Thread selection is limited to 1-4.

The full **Erstellen & Dekodieren** settings page follows the screenshot ordering and contains the Framework Manager and AAPT2 Manager directly, rather than a separate Apktool-A application UI.

## Parallel job model

`ApktoolJobService` supports up to four top-level jobs with unique IDs, queue/running/succeeded/failed/cancelled states, per-job logs, individual Stop and Stop all. `Future.cancel(true)` is paired with cooperative interruption and AAPT2 subprocess termination. Provisioning and framework changes are locked, while ordinary decode/build work can run concurrently. Apktool's internal thread budget remains bounded at 1-4 and is reduced when multiple top-level runners are active.

## CI workflow

`.github/workflows/build-debug.yml` is included. It checks out the tree, sets up Temurin JDK 17, installs Android platform 36, build-tools 36.0.0 and NDK `29.0.14033849`, verifies the expected source-module directories, prints the repository Gradle/toolchain versions, executes:

```text
./gradlew :app:assembleDebug --stacktrace --no-daemon
```

and uploads the debug APK artifact for 14 days.

## Validation status

Static validation checks the module graph, project dependency paths, source roots, XML/TOML/configuration syntax and required framework/AAPT2 payload locations. A full Gradle build cannot be run in this container because the Gradle distribution is not present and outbound resolution of `services.gradle.org` is unavailable. The GitHub Actions workflow therefore remains the authoritative compile test.
