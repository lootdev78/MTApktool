# Installed Apps / APK Extractor UI update - 2026-09-19

The integrated APK extractor now follows the supplied MT-style Installed Apps workflow while retaining MTApktool paths and AntiSplit handling.

Implemented in this update:

- Left drawer entry renamed to `Installed Apps`.
- Installed Apps top bar with back, search and overflow menu.
- USER APP / SYSTEM APP tabs.
- Long-press multi-selection with `Selected: N` title and selected-row highlight.
- Overflow entries: Sort, Select all, Preferences.
- Multi-selection actions: cancel, uninstall, select all, extract.
- Confirmation dialogs for multi-extract and multi-uninstall.
- App information dialog matching the supplied compact layout: app icon/name/version, package/version code/file size/signature/protection/SDK/data/APK/install/UID fields.
- App information actions: MORE -> Launch / Details / Uninstall and EXTRACT APK.
- Preferences dialog with APK storage path, name pattern tokens and optional signature verification.
- Name patterns are applied to real extracted files.
- Split applications are exported as `.apks`; normal applications as `.apk`.
- Output continues to publish back to the explorer so the generated file can be revealed/highlighted by the existing output bridge.
- Extractor engine now exposes base/split byte sizes and filename-aware extraction overloads.

The default MTApktool extraction root remains `/storage/emulated/0/apktool/apks`, as requested earlier, rather than copying the screenshot's MT2 path.
