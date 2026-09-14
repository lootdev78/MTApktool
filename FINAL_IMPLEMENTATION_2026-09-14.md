# Final implementation status — 2026-09-14

This project tree is the finished source package for the requested MT-style MTApktool workflow. It keeps Apktool work in the dedicated `:apktool` process and does not add an internal text editor.

Implemented areas include interactive left/right drawers; dual-pane output targeting; real Settings section activities; APK information/activity workflow; APKS/APKM/XAPK/APKX inspection, selection, one-session install, extraction and Split→APK conversion; embedded zipalign options and verification; read-only ZIP/JAR/APK/7z/TAR-family/RAR browsing with copy/extract-out; SAF Add Location; persisted hidden/filter/sort/per-folder overrides; search toolbar behavior; APK/container icon loading; and automatic explorer refresh after successful Apktool jobs.

Decoded-source app-default output is `/apktool/projects/<app-name>/`. Build and conversion dialogs expose pane-aware and custom targets.

## AntiSplit-M source note

The supplied source archive contains `ANTISPLIT_LICENSE.md` but no AntiSplit-M source directory/module. The project therefore includes the requested APKS/APKM/XAPK/APKX UI/workflow and an internal compatibility Split→APK merger backed by the available source tree, plus the supplied zipalign source. It does not falsely claim that absent upstream AntiSplit-M implementation files are present.

## Validation

No Gradle build/assemble task was run, as requested. The final packaging process performs XML parsing, stale-reference checks, source sanity checks and ZIP integrity verification.
