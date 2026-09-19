# MTApktool UI / SAF fixes - 2026-09-19

This source update uses the supplied MTApktool screenshots as the visual reference.

## Explorer window and theme

- File-manager content consumes `WindowInsets.safeDrawing`; it does not occupy the Android status/navigation-bar areas.
- Android 15/16 edge-to-edge is handled by the root window while Compose applies the safe insets.
- Dark reference palette: background `#121318`, active surface `#191C21`, explorer toolbar/bottom surface `#2C2C2C`, accent `#9ECAFF`.
- The active pane uses the MTApktool surface color and the inactive pane uses the MTApktool background color.
- APK Split, APK Extractor, dialogs, drawer and embedded text editor inherit the same host theme.

## Add storage / SAF

- `Add storage` requests a persistent read/write document-tree grant.
- A tree that does not expose a persistent write grant / writable directory capability is rejected rather than mounted read-only.
- After adding a storage tree, MTApktool opens its options so a custom name can be entered immediately.
- Long-press an added storage location to rename or remove it. Removing it releases the persisted URI permission.
- SAF locations implement create, rename, delete, local↔SAF copy/move and SAF↔SAF copy/move.

## Dual-pane operations

- Long-press file/folder actions perform real copy/move to the opposite pane.
- The destination panel refreshes and newly created output is scrolled into view / highlighted.
- Long-press supports `Open as text` and `Open archive` where applicable.

## Split/APKExtractor

- AntiSplit/APKS/APKM/XAPK/APKX actions remain dialogs and use the MTApktool host theme.
- APK Extractor is a host screen in the left navigation, with only the integrated APK/APKS/merge/info actions.

## Build-error fixes retained

- No copied `antisplit-m/src/main/java/org/xmlpull/v1/*` sources; the shared XMLPull dependency is used.
- No `setSyntaxDarkMode` call in MH TextEditor.
- The obsolete `SettingsActivities.kt` source that produced Boolean/String call mismatches is not present in this tree.
