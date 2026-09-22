# MTApktool recovered project status — 2026-09-20

This project is the recovered full state assembled after the later ZIPs had regressed to an older source snapshot.

## Restored project layers

- Custom MTApktool launcher icon restored in adaptive, monochrome, round and legacy mipmap variants.
- MT-style flat UI layer restored: shared 48 dp classic top/bottom chrome, compact dialogs, flat surfaces, zero/low-radius shapes and common action vectors.
- Explorer drawer restored without branding header or Network section; add-local-storage lives only in the drawer overflow menu; Key & Certificate Manager is a direct tool entry.
- Bookmark pull-up sheet restored from both normal and selection bottom bars.
- Selection bottom bar restored to explicit Select all / Copy / Move / Delete / More semantics.
- Archive session model restored: OPENING/CLEAN/DIRTY/UPDATING/FAILED/CLOSED, password retry/cache, dirty-entry tracking, nested parent/child sessions, Update/Discard/Cancel, transactional repack, metadata preservation, extraction conflicts and archive tasks.
- Apktool project workflow restored: QUEUED/PROVISIONING/DECODING/POST_DECODE/READY/DIRTY/BUILDING/POST_PROCESSING/VERIFYING/SUCCEEDED/FAILED/CANCELLED, cross-process project state and apksig verification.
- Text editor restored with real save/undo/redo/search/replace, decoded-project dirty tracking, current-folder/recent-file navigation and Android-navigation-bar-safe bottom controls.
- Image viewer and Media3 audio/video player restored and wired to both explorer panels.
- Key/signature settings remain backed by the existing apksig-android/zipalign-android integration.

## Removed again

The last added `mt-data-files-provider` module was removed completely after the startup-crash report. No module directory, Gradle include/dependency, provider manifest entry or source reference remains.

Root/Shizuku/Shell-startup options and the pseudo Root Directory storage entry are not present. The drawer has no Network section.

## Deprecated APIs replaced rather than suppressed

The CI-warning families reported by the user were migrated in source instead of hidden:

- system bar color setters -> edge-to-edge / WindowInsetsControllerCompat paths
- old Notification.Action integer-icon constructor -> Icon-based action builder
- directional Material icons -> AutoMirrored variants
- Material Divider -> HorizontalDivider
- old LocalLifecycleOwner import -> lifecycle-runtime-compose location
- old transformable callback -> centroid-capable overload
- deprecated Commons Compress XZ/Zstd constructors -> builder APIs
- TarArchiveInputStream.nextTarEntry -> nextEntry
- old external-storage root/files APIs -> shared-storage compatibility layer
- old editor AsyncTask/default Handler/legacy display/reflection/storage-permission paths -> executor/main-looper/insets/compat implementations
- private Media3 showBuffering field access is not used

No `@Suppress("DEPRECATION")` or Java deprecation suppression was introduced to hide these warnings.

## Validation performed

- 131 production Android XML resources/manifests parsed successfully.
- TOML parsed successfully.
- 2,273 Kotlin/Java source files passed the delimiter/structure scan.
- `ApktoolWorkflow.kt` compiled independently with kotlinc.
- `gradlew17` and `gradlew25` pass shell syntax checks and retain their Java-profile wiring.
- Project-wide scans report zero remaining matches for the CI deprecation patterns above, zero raw Material3 AlertDialog/TopAppBar/Card calls in the app UI, zero references to the removed provider module, zero Root/Shizuku startup references and zero Network-drawer entries.

A complete Android Gradle compile could not be run in this container because the Gradle 8.11.1 wrapper distribution is not cached and network access to services.gradle.org is unavailable. The attempted offline build stops before project configuration with UnknownHostException while fetching the wrapper; it does not reach Kotlin/Android compilation.

## Reference limitation

The MT Manager reference ZIP remains available in the conversation, but its materialized path is not visible to the execution container in this run. No new exact Smali symbol or resource-ID claims were invented during this recovery pass.
