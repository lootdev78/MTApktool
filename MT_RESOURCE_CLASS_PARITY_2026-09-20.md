# MT Manager → MTApktool parity map (archive + Apktool)

This pass reconstructs behavior in MTApktool-owned Kotlin/Compose code and open-source libraries. No proprietary MT bytecode/native library is embedded.

The supplied MT Manager ZIP is present in the conversation, but its ZIP members are not exposed to the execution container in this run. Therefore exact original `0x7f...` resource IDs and obfuscated Smali class names are deliberately **not invented** below. The MTApktool side and handler/state flow are implemented and ready for a later symbol-level annotation pass when those members are directly readable.

| Behavior / original UI target | Original MT resource / class | MTApktool implementation | State / event flow |
|---|---|---|---|
| Open archive | symbol not runtime-resolved | `archive/ArchiveSessionManager.kt`, `ExplorerViewModel.openArchive` | OPENING → CLEAN / FAILED; password retry; task progress |
| Archive password prompt/cache | symbol not runtime-resolved | `ArchivePasswordRequest`, `ArchiveSessionManager.passwordVault` | prompt only after password/encryption failure; successful session caches in RAM only |
| Edit archive entries | symbol not runtime-resolved | mounted workspace + normal explorer operations | any rename/delete/copy/editor save marks DIRTY |
| Leave modified archive | symbol not runtime-resolved | `ArchiveUpdateRequest` in `ExplorerScreen` | UPDATE / DISCARD / CANCEL; no lifecycle auto-save |
| Repack/update | symbol not runtime-resolved | `ArchiveEngine.replaceFromDirectory` | temp archive → verification → atomic replace → CLEAN; failure leaves original untouched |
| Nested archive | symbol not runtime-resolved | `ArchiveSessionSnapshot.parentId/depth` | child session commits into parent workspace and marks parent DIRTY |
| Extract conflict | symbol not runtime-resolved | `ArchiveEntryConflict` + existing `FileConflictDialog` | OVERWRITE / SKIP / KEEP_BOTH / CANCEL, including apply-to-all |
| Archive task list | symbol not runtime-resolved | `ArchiveTaskInfo`, `ApktoolTaskPanel` | OPEN/CREATE/EXTRACT/UPDATE; progress/cancel; terminal auto-removal |
| ZIP metadata | symbol not runtime-resolved | Commons Compress + Zip4j in `ArchiveEngine` | comments/extra/unix mode/method/time retained best-effort; encrypted ZIP uses Zip4j path |
| TAR metadata | symbol not runtime-resolved | Commons Compress in `ArchiveEngine` | mode/uid/gid/user/group/time/symlink retained best-effort |
| Decode APK | symbol not runtime-resolved | `ApktoolDecodeDialog`, `ApktoolJobService` | QUEUED → PROVISIONING → DECODING → POST_DECODE → READY/FAILED |
| Decoded-project state | symbol not runtime-resolved | `ApktoolWorkflow.kt` | READY / DIRTY / BUILDING / FAILED / SUCCEEDED; changed-file list |
| Cross-process project state | n/a (MTApktool architecture) | app-private `apktool-project-sessions/*.properties` | main process and `:apktool` worker share source/status/output/error atomically |
| Build project | symbol not runtime-resolved | `ApktoolBuildDialog`, `ApktoolJobService` | QUEUED → BUILDING → POST_PROCESSING → VERIFYING → SUCCEEDED/FAILED |
| Align/sign after build | symbol not runtime-resolved | existing `zipalign-android` / `apksig-android` workflow | one job; output stays attached to project session |
| Verify built APK | symbol not runtime-resolved | `com.android.apksig.ApkVerifier` | successful APK output is verified before SUCCEEDED |
| Editor changes inside decoded project | symbol not runtime-resolved | `CodeEditorScreen` → `ApktoolProjectSessionManager.markFileChanged` | project becomes DIRTY after successful save |
| Split-container decode | symbol not runtime-resolved | `SplitArchiveSupport` + decode job registration | every produced directory containing `apktool.yml` becomes its own project session |
| Task detail/output | symbol not runtime-resolved | `ApktoolTaskPanel`, `ApktoolJobsViewModel` | workflow phase is broadcast through `EXTRA_STAGE` rather than generic RUNNING only |

## Build fixes included in this pass

- `CodeEditorScreen.kt`: removed the unresolved `UiText` / `.show` placeholder handlers and implemented undo/redo/search/replace/save.
- `ImageViewerScreen.kt`: no Material3 experimental top-app-bar API is used.
- `MediaPlayerScreen.kt`: no access to private `PlayerView.showBuffering`; Media3 controller setters are invoked through compatible public/reflection hooks.
- Media3 ExoPlayer/UI dependencies are included in `app/build.gradle.kts`.

## Remaining symbol-level work

Only the **name/ID annotation** side of the requested `Original layout → resource ID → Smali class → handler → MTApktool class` chain is unresolved in this runtime, because the uploaded decompiled/reference ZIP cannot be enumerated from the execution container. The MTApktool handler/state targets listed above are already separated so those original symbols can later be attached without another engine redesign.
