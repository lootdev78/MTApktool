# Archive + Apktool reconstruction pass — 2026-09-20

## Archive engine

The previous implicit “extract to temp and silently repack” behavior has been replaced by an explicit archive-session model:

- `OPENING`, `CLEAN`, `DIRTY`, `UPDATING`, `FAILED`, `CLOSED` states.
- Per-session workspace and in-memory password cache.
- Dirty-entry tracking by relative path.
- Nested archive parent/child sessions.
- Explicit Update / Discard / Cancel when leaving a modified archive.
- Pending archive-open continuation after Update/Discard.
- Transactional repack: write temp → verify readable → atomic replace.
- Conflict resolver for extraction with overwrite, skip, keep-both and cancel; the existing explorer conflict dialog and apply-to-all state are reused.
- ZIP/TAR metadata retention paths and symlink handling.
- Archive OPEN/CREATE/EXTRACT/UPDATE task records with cancellation hooks and automatic removal of terminal tasks.

RAR writing is not implemented because no proprietary MT writer is included and the current open-source dependency set does not provide a verified RAR writer in this project.

## Apktool port workflow

The Apktool port is now modeled as a project workflow rather than only a shell job:

- `QUEUED`, `PROVISIONING`, `DECODING`, `POST_DECODE`, `READY`, `DIRTY`, `BUILDING`, `POST_PROCESSING`, `VERIFYING`, `SUCCEEDED`, `FAILED`, `CANCELLED`.
- Decoded project/source-APK association.
- Split-container decode registers each produced `apktool.yml` project.
- Editor saves mark decoded projects DIRTY.
- Build dialog shows changed-file/project state.
- Align/sign remains part of the same build task.
- APK verification runs before a successful APK build is reported complete.
- `:apktool` is a separate Android process, so project state is mirrored atomically through an app-private properties store; it is not kept only in a process-local singleton.
- Cleanup of the project `build/` directory is independent from project identity/state tracking.

## Compiler errors addressed

The exact reported source patterns no longer exist:

- unresolved `UiText` / `.show` in `CodeEditorScreen.kt`
- Material3 experimental top-app-bar usage in image/media screens
- direct/private `PlayerView.showBuffering` access

A full Gradle build cannot be completed in this environment because the Gradle wrapper distribution is not cached and network access is disabled. XML/TOML/source-structure checks and pure-Kotlin compilation of the new Apktool project-state layer are used instead.
