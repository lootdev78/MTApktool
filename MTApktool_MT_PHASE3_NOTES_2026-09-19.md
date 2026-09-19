# MTApktool MT file-manager refinement – Phase 3

Cumulative overlay. Apply directly to the project root; previous overlay ZIPs are not required.

## Added/refined
- MT-style bookmark bottom sheet opened by swiping upward from the file-manager bottom bar.
- Bookmark groups, ordering, add-to-top preference, move/delete/rename group workflows and pane-position-aware opening.
- Sidebar bookmark visibility preference while retaining all bookmark groups in the pull-up bar.
- Selection-menu bookmark workflow supports adding multiple selected items to a chosen group.
- MT-style selection/bottom-bar and active-pane transition animations.
- Create/Rename dialog layout polish; rename preselects basename while keeping extension.
- Text editor bottom special-character/function/search area respects Android navigation insets on Android 15+.
- Text editor classic light/dark colors aligned with the file-manager theme.
- New MTApktool launcher icon: geometric M plus APK/package cube, adaptive/monochrome and density fallbacks.
- Includes all Phase 1/2 compile, DEX/FileProvider, archive-task, classic-theme and MT-icon fixes.

## Verification
- Resource XML parse validation completed successfully.
- Kotlin/Java delimiter/static sanity checks completed for modified files.
- Full Gradle build cannot run in the current environment because Gradle 8.11.1 is not cached and external network access is unavailable.
