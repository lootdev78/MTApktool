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

- The duplicate per-pane path rows are removed; only the top explorer navigation shows the active path.
- Tapping the top path opens **Jump to path** for the active pane.
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


## MTApktool UI / SAF integration follow-up (2026-09-19)

- Explorer content now consumes Android `safeDrawing` insets, so the toolbar, dual panes and bottom actions stay outside the status/navigation bars even with Android 15/16 edge-to-edge enforcement.
- Dark colors are aligned to the supplied MTApktool reference screenshots: explorer background `#121318`, active surface `#191C21`, toolbar/bottom surface `#2C2C2C`, primary accent around `#9ECAFF`.
- APK Split/AntiSplit and APK Extractor use the same host Material theme rather than a separate module theme.
- `Add storage` requires a persistent writable SAF tree. A read-only grant is rejected instead of being silently added. Newly added storage immediately opens its name/options dialog. Long-press a storage location to rename or remove it.
- Custom SAF storage supports create/rename/delete and real copy/move between left and right panes, including local↔SAF and SAF↔SAF transfers.
- Long-press file actions include real copy/move to the opposite pane, open-as-text through MH TextEditor, and open-as-archive for supported archives.
- AntiSplit-M no longer contains duplicate `org.xmlpull.v1` sources; it uses the shared XMLPull dependency. The obsolete `setSyntaxDarkMode` editor call is absent.

## 2026-09-19 MT-classic core pass

- Pane pointer focus now drives the single top path/status header; active pane gets a subtle inward shadow.
- Added classic file-conflict workflow with overwrite/keep-both/skip/cancel/apply-all.
- Added real recycle-bin routing including SAF -> local recycle bin, auto cleanup and drawer access.
- Writable SAF locations now support rename/delete/hide/sort and persistent drawer/tool ordering.
- Split-package merge gained automatic signing through the existing `:apksig-android` `SignWrapper` and MTApktool signature settings.
- Drawer/task/pane surfaces and global shape system moved closer to the supplied black/gray/blue MT-style references.
