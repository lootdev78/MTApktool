# MTApktool – Apktool/UI wiring update

This update wires the file-manager UI to the bundled Apktool runtime instead of treating decode/build as isolated dialogs.

## Apktool runtime and frameworks

- Decode/build/CLI jobs are enqueued through `ApktoolJobService` and executed by the bundled `ApktoolCommandRunner`.
- Bundled SDK 33/34/35/36 framework APKs are provisioned from Android `res/raw` resources, with the previous asset locations kept only as fallback. This avoids the `FileNotFoundException: apktool/frameworks/sdk-33.apk` seen on affected builds.
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
