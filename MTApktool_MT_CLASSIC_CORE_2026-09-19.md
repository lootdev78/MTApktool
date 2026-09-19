# MTApktool MT-classic explorer/core pass - 2026-09-19

This source snapshot applies the 2026-09-19 screenshot-driven explorer workflow to MTApktool while keeping MTApktool's own toolchain/modules.

## Dual panel / navigation

- The single path/status header always follows the last pane touched. Pane focus is captured on pointer-down, before a child file row consumes the event.
- The last-used pane receives a subtle inward four-edge shade instead of a bright selection border.
- Drawer styling uses the classic black/gray/blue visual hierarchy from the supplied references.
- Local roots show usage bars; writable SAF locations are shown in the same Local group with a readable `/storage/...` approximation where possible.
- Added SAF locations keep persisted read/write access. Long press provides Rename, Delete, Hide and Sort. Sort mode shows drag handles and persists custom-location/tool order.
- Drawer tools include Installed Apps, Text Editor and (when enabled) Recycle Bin.

## File operations

- Copy and Move operate between left/right panes for local<->local, local<->SAF and SAF<->SAF.
- Destination panes are locked while a transfer is active and show the current operation and percentage.
- Existing destinations use an MT-style `File already exist` prompt with Overwrite, Keep both, Skip, Cancel and Apply to all.
- Delete can move local or SAF items into MTApktool's recycle bin. Permanent deletion keeps the configured warning.
- Recycle-bin enable/default/auto-clean/warning settings are functional; the drawer entry opens the recycle-bin directory and the explorer overflow can empty it.
- New outputs are revealed in the initiating pane, scrolled into view and highlighted temporarily.

## APK / split packages

- `.apk` keeps the compact APK-information/functions workflow.
- `.apks`, `.apkm`, `.xapk` and `.apkx` use the compact split-selection dialog.
- Split install uses one PackageInstaller session containing the selected APKs.
- `ZU APK` uses the integrated AntiSplit-M source engine.
- The merge dialog has `Automatisch signieren`, enabled by default, and uses the existing MTApktool signature profile/schemes through the integrated Java `SignWrapper` from `:apksig-android`.
- APK extraction remains backed by the integrated APKExtractor engine; APK cloning remains backed by the integrated APKCloner Java engine.

## Theme

- Dark palette is aligned to the supplied classic references: black toolbar/navigation surfaces, #303030 working/dialog surface family and blue action/accent color.
- Global Material shapes are flattened to 0-2dp so existing Compose controls stop looking like large Material-3 cards while retaining the existing Compose implementation.
- System status/navigation bars remain outside the explorer content through `safeDrawing`.

## Validation limitation

The project is statically checked in this environment. A full Android Gradle compile is not possible here because the container has JDK 21 and no Android SDK, while the checked-in project profiles target the Java 17 and Java 25 CI environments.
