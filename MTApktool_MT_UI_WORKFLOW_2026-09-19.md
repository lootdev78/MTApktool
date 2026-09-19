# MTApktool MT-style UI/workflow update — 2026-09-19

This source snapshot applies the MT-style workflows requested from the supplied screenshots while keeping the existing MTApktool dual-pane architecture.

## Explorer and dialogs
- MT-style long-press file menu: Copy/Move, Delete/Rename, Tools/Compress, Properties/Share, Open with/Add bookmark.
- File-menu order is configurable by long-press drag.
- Built-in open methods are limited to MTApktool capabilities and filtered by the selected file type.
- Android ACTION_VIEW/ACTION_EDIT routes into the same MTApktool built-in chooser; signing is not an opening method.
- "Show in last used panel" uses the persisted last active left/right pane.
- APK information and signature-information dialogs use compact MT-style surfaces and bottom actions.
- APKS/APKM/XAPK/APKX actions remain a dialog and use the integrated AntiSplit-M engine.

## Archive browsing
- ZIP/JAR/APK and supported 7z/tar/gzip/xz family archives are mounted into a temporary workspace and displayed as a directory in the selected pane.
- Only the pane doing the archive work is frozen while opening.
- The pane displays a loading spinner, label and 0–100% progress until the extracted workspace has been listed and is browseable.
- Archive edits continue to use the existing commit/repack workflow.

## Preferences copied from the supplied workflow references
- theme mode and theme color palette / Monet choices;
- file-list size, filename line count, list time/date formatting;
- startup path for left/right panes;
- sort file menu and sort built-in opening methods;
- custom MTApktool workspace;
- backup/preserve-file-time options;
- recycle-bin behavior and deletion warning;
- APK installation verification;
- external-storage thumbnail and transfer options.

## Tool integration
- MH TextEditor is offered only for supported text/code types (or matching text MIME types from Android Open with).
- Apktool decompile is offered only for APK/split-package inputs.
- Archive viewer is offered only for archive-compatible types.
- APK Extractor uses MTApktool theme/dialog styling and exposes the relevant APK/APKS/AntiSplit operations.
- Successful generated outputs are returned to the executing pane, scrolled into view and temporarily highlighted.

## Static validation in this environment
- Main manifests/resources are XML-well-formed.
- No source merge-conflict markers.
- The previously reported bundled `org/xmlpull/v1` AntiSplit sources are absent.
- The previously reported missing `setSyntaxDarkMode` call is absent.
- Java 17 / Java 25 wrapper shell scripts pass shell syntax validation.

A full Android Gradle build is not claimed here: this container has JDK 21, no Android SDK, and cannot download the required Gradle distributions because network access is disabled.
