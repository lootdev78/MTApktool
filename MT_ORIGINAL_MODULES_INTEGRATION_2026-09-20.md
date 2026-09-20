# MT-origin module integration — 2026-09-20

## Integrated in-tree: MTDataFilesProvider

Source: user-provided `MTDataFilesProvider-main.zip`.

MTApktool module: `:mt-data-files-provider`

Integration details:
- Keeps the original `bin.mt.file.content.MTDataFilesProvider` and `MTDataFilesWakeUpActivity` source.
- Keeps the original DocumentsProvider manifest contract:
  - authority `${applicationId}.MTDataFilesProvider`
  - exported DocumentsProvider protected by `android.permission.MANAGE_DOCUMENTS`
  - wake-up Activity
  - MT extension calls for last-modified, permissions and symlink creation.
- Built directly in-tree; no JitPack repository/dependency required.
- Uses MTApktool's `mtapktool.javaVersion`, `mtapktool.compileSdk` and `mtapktool.minSdk` Gradle properties.
- Therefore the same module source follows both `gradlew17` and `gradlew25` profiles.
- Added to the main app with `implementation(project(":mt-data-files-provider"))`, so its provider/activity are merged into the final APK manifest.
- Existing MTApktool “Add local storage” flow continues to use Android `OpenDocumentTree`, so provider roots are consumed through the same SAF workflow.

The upstream archive supplied by the user contains no LICENSE/COPYING file. `README_UPSTREAM.md` and `UPSTREAM.md` are retained in the module so provenance is explicit.

## Uploaded modules inspected but not compiled into MTApktool

`ApkSignatureKillerEx-main.zip` contains runtime/native hooking specifically intended to defeat APK signature/integrity checks (`xhook`, `openat`, `KillerApplication`, embedded `origin.apk`).

`ApkDataMultiplexing-main (1).zip` contains a custom ZIP central-directory/data-offset multiplexing implementation plus a custom V2/V3 signer designed to preserve that representation after signing.

No replacement or alternate module was created for either archive.
