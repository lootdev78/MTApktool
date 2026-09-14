# MTApktool update notes — 2026-09-14

This source package was updated from `MTApktool-full-archive-panel-edit-extract.zip` according to the supplied MT Manager/MTApktool screenshots and workflow notes.

## Implemented in this source package

- Real Android settings activities for the settings root and the Application, Apktool, Signature, Archive, Framework, AAPT2 and APK Modules sections.
- APK information activity with icon/package/version/SDK/signature/protection/install details and FUNCTION / VIEW / INSTALL actions. VIEW returns to the explorer and opens the APK as an archive.
- APKS/APKM/XAPK/APKX package activity with split listing, one-session PackageInstaller installation, base/all extraction, decode, and Split-to-APK conversion controls.
- Split-to-APK conversion output choices for source folder, left panel, right panel, app default `/apktool/projects/<name>/`, or a custom path, plus merge/zipalign options.
- Existing `zipalign-android` source is used directly. Alignment, shared-library alignment, overwrite and verification are exposed.
- Decode/build output locations can target source folder, left panel, right panel, app default or a custom path. Decode app-default is `/apktool/projects/<app-name>/`.
- Decode/build advanced flags include frame path (`-p`), shared libraries (`-l`), quiet/verbose handling, and build `--no-apk`. The previously incorrect `preserve folder structure -> --match-original` mapping was removed.
- Left navigation and right task drawers now share finger-following drag progress, velocity/threshold settling, progress-tied scrims and swipe-to-close behavior. Clear-finished only removes terminal jobs.
- Explorer search toolbar retains hamburger, close and overflow actions.
- Hidden-files UI is an overflow-style popup; hidden/filter/sort defaults, manual-hidden entries and per-folder sort overrides are persisted and manageable.
- Add Location uses Android's Document Tree picker and persists URI permissions. Added trees can be opened read-only from the drawer.
- ZIP/JAR/APK, 7z, TAR family and RAR browsing/extraction support. Opened archives are intentionally read-only: copy/extract out is allowed; modifications inside archive workspaces are blocked.
- Successful Apktool jobs refresh both panes so newly created/changed outputs receive the existing temporary green-change highlight.
- Internal text-editor navigation from normal explorer taps is disabled; text files are handed to an external/open-with activity instead.

## AntiSplit-M source status

The input ZIP was inspected before packaging. It contains `ANTISPLIT_LICENSE.md`, but **does not contain an AntiSplit-M source tree or module**. The only AntiSplit-related entry in the original ZIP is that license file. Therefore this update does not falsely add or claim an unavailable AntiSplit-M source module.

To keep the requested workflow usable, MTApktool now contains an integrated Split-to-APK compatibility workflow for APKS/APKM/XAPK/APKX (base/universal selection, split extraction/merge, zipalign, verification and one-tap split install). If the exact AntiSplit-M source tree is later supplied, its merge engine can replace the compatibility merge behind `SplitPackageTools` without changing the new UI/workflow.

## Validation constraint

Per the request, no Gradle assemble/build task was run. The package was checked structurally and the existing JDK17/JDK25 workflow files were not intentionally rewritten.
