# MTApktool – Apktool/UI wiring update

This update wires the file-manager UI to the bundled Apktool runtime instead of treating decode/build as isolated dialogs.

## Apktool runtime and frameworks

- Decode/build/CLI jobs are enqueued through `ApktoolJobService` and executed by the bundled `ApktoolCommandRunner`.
- Bundled SDK 33/34/35/36 frameworks are stored as their real `.apk` assets under `apktool-android/src/main/assets/apktool/frameworks/`; `.gitignore` explicitly keeps those source APKs tracked.
- The default framework mirror `1.apk` is provisioned from SDK 36 and tagged framework files remain available as `1-sdk33.apk` … `1-sdk36.apk`.
- Framework manager, AAPT2 manager, signatures, job/threads paths, runtime info and the complete original Apktool CLI remain reachable from **Einstellungen > Erstellen & Dekodieren** / **Apktool CLI**.

## Jobs / right task panel

- Confirming Decode, Build or CLI immediately opens the right-side Task panel.
- The panel shows queued/running/succeeded/failed/cancelled jobs, current output line, result path and stop controls.
- It can also be opened by swiping left from the right side of the bottom navigation and dismissed by swiping it to the right.

## Dual-pane navigation

- Both file panes show their own clickable current path.
- Tapping a pane path opens **Jump to path** for that pane.
- The global active path is clickable as well.
- The Jump-to-path dialog now uses one state for typing/paste/OK, fixing the old stale-value behavior.

## Archive creation

A new MT-style archive dialog supports:

- `zip`, `7z`, `tar`, `tar.gz`, `tar.xz`, `tar.zst`, `tar.bz2`, `tar.lz4`, `gzip`, `xz`
- Store / Fastest / Fast / Normal / Maximum / Ultra / APK mode
- ZIP/7z password encryption
- optional split length
- compress each selected item independently
- delete sources after successful compression
- write to the opposite file-manager pane

Archive defaults can be changed under **Einstellungen > Archivierung**.

## Settings UI

The new settings root follows the supplied MT-style structure: Anwendung, Erstellen & Dekodieren, Signatur, Archivierung, Apktool CLI, FAQ and Über.

## Build/update profile

The Android platform profile is centralized in `gradle.properties`:

```properties
mtapktool.compileSdk=36
mtapktool.minSdk=29
mtapktool.targetSdk=36
mtapktool.ndkVersion=29.0.14033849
```

All included Android application/library modules read the shared values. JVM-only modules continue to inherit the root Java profile (`mtapktool.javaVersion=17` or 25).

## Validation note

The code was statically checked in the provided environment. A complete Android Gradle build could not be executed there because the matching Gradle distribution/dependency cache was not present and outbound Gradle downloads were unavailable. Run `./gradlew assembleDebug` with JDK 17 (or the existing Java-25 profile/wrapper for that profile) in the normal Android build environment before installing.

## CI / bundled framework follow-up

- Restored the original two GitHub Actions workflow files; no `.github/scripts` helper is required.
- Java 17 and Java 25 verify the same SDK 36 / NDK 29 profile inline, including the pinned `libs.versions.toml` values.
- All Android application/library modules are checked for the shared `mtapktool.compileSdk` / `mtapktool.minSdk` wiring.
- Bundled framework files use their real `.apk` asset names. The repository exception keeps them tracked while `Toolchain` reads them directly from assets, so no generated `R.raw.apktool_framework_sdk_*` symbols are required.
- The bundled debug keystore is likewise stored as a `.bin` asset so the `*.keystore` ignore rule cannot remove it in CI checkouts.
## AAPT2-only / build-script cleanup

- Removed the obsolete AAPT toggle and every AAPT1 reference from the app UI.
- Removed the `APKTOOL_DUMMY` switches from both decode/settings UIs; Apktool's internal unresolved-resource handling remains internal.
- The AAPT2 Manager now exposes only meaningful AAPT2 choices, validates custom binaries as AAPT2, and migrates the old `sdk36` alias to the automatic recommended selection.
- Core CLI and Android runner reject legacy AAPT binaries instead of carrying an AAPT1 execution branch.
- Removed accidental Git merge markers from `app/build.gradle.kts`; Compose and `BuildConfig` are enabled in one clean `buildFeatures` block.
- The JDK 17 legacy Gradle profile keeps Gradle 8.11.1/AGP 8.10.1 and suppresses only the Kotlin plugin's future Gradle-deprecation warning; the separate JDK 25 workflow remains on Gradle 9.1/AGP 9.0.


## Job-Output / responsive Apktool runtime
- Apktool jobs run in a dedicated Android process (`:apktool`) so decode/build cannot block the Compose UI process.
- Worker threads use background priority and UI status broadcasts are throttled; the complete on-device log file is still written without throttling.
- A bounded live output tail is shown in a hideable job dialog. Hiding the dialog does not stop the task.
- Jobs can be reopened by swiping the bottom navigation from the right edge toward the center and tapping `OUTPUT` on a task.
- Tapping a normal `.apk` now opens an action chooser: `Dekompilieren` or `Als Framework importieren`.
- Framework import uses the same `/apktool/frameworks` directory/`install-framework` command as the existing Framework Manager and runs as a background Apktool job.
- `.apks`, `.xapk` and `.apkm` continue directly to the split/container decode dialog.

## Archive panel integration (2026-09-14)

- Supported archives can now be opened in the panel they were selected from instead of being handed directly to another app.
- Added editable temporary archive workspaces with automatic write-back to the original archive after explorer mutations and when returning from the editor.
- Added MT-style Extract dialog: current directory, custom relative output path, opposite panel destination, delete source after extraction, and ZIP/7z password field.
- Long-press file actions now expose Extract for supported archives.
- Archive virtual paths are shown as `<archive>!/path` while browsing; Jump to path understands the same virtual path.
- ZIP, 7z, tar, tar.gz, tar.xz, tar.zst, tar.bz2, tar.lz4, gzip and xz are wired through the existing archive dependencies. No new Gradle dependency or shell helper script was added.

## 2026-09-15 integrated source modules

- Added real AntiSplit-M source module and wired APKS/APKM/XAPK/APKX conversion to it.
- Added APK Extractor to the left navigation with default `/apktool/apks` output and centralized settings.
- Added APK Cloner as an `.apk` function with pane-aware/custom output selection.
- Added MH TextEditor source modules and MTApktool theme/settings integration.
- New modules use the same shared compile SDK/min SDK/NDK and Java 17/Java 25 build profiles as the existing project.
