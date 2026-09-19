# MTApktool file-manager implementation notes

This project recreates the requested dual-pane file-manager workflows in MTApktool code. The supplied MT Manager build was used as a behavioral/visual reference. No MT Manager source code, native archive engine, root/Shizuku integration, or other proprietary precompiled MT library is bundled by this project.

## Archive backend

The archive implementation is owned by MTApktool and composes auditable/open-source Gradle dependencies:

- Apache Commons Compress — tar, tar.gz, tar.xz, tar.bz2, tar.zst, tar.lz4, gzip, xz, bzip2, zstd, lz4, 7z
- Zip4j — ZIP/APK/JAR/split-package ZIP containers, passwords/AES
- XZ for Java — XZ support used by Commons Compress
- zstd-jni — Zstandard codec
- junrar — RAR extraction (read-only)

RAR creation/update is intentionally not claimed. RAR workspaces are protected against in-place writes; edited external workspace content is reported as read-only and can be extracted instead.

ZIP/APK/JAR/7z/tar-family and supported single-stream formats can be rebuilt from the archive workspace. Replacement uses a temporary archive and an atomic move where the filesystem supports it. Editing a file with the built-in editor or another app is detected when the explorer resumes and prompts before rebuilding the archive. APK content updates warn that the previous APK signature becomes invalid.

## Media

AndroidX Media3 provides audio/video playback. Coil is used by the image viewer. The image viewer supports sibling-image paging, pan/zoom and double-tap zoom.

## File-manager model

- Independent left/right navigation and history.
- Parent-directory workflow is pane-local.
- Selection toolbar follows the reference interaction order: Copy, Move, Delete, More, Done.
- Right Tasks panel includes large copy/move/delete operations, archive work, and the existing MTApktool Apktool jobs.
- Long-press surfaces a file-specific primary action only when MTApktool has an implementation for that file type, followed by generic file actions.
- Bottom-bar upward gesture opens grouped bookmarks and can target the pane under the gesture.
- No root or Shizuku path is provided.

## Apktool

APK decode/build/signing/tooling continues to use the MTApktool Android Apktool port and existing project implementation. No MT Manager APK-tool implementation is included.

## UI / credits

The classic dual-pane workflow and visual behavior are recreated for familiarity from the supplied MT Manager reference. MT Manager and its trademarks/assets belong to their respective owners. See Settings -> FAQ / Credits in the app for user-facing credits and dependency attribution.

## Language

New/refined file-manager, archive, tasks, bookmarks, media, settings and update-dialog UI uses the central English/German language bridge and follows the device language (German for `de`, English otherwise). Technical tool names and file-format names remain unchanged.
