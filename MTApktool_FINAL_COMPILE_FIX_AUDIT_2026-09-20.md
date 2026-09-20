# MTApktool final compile-fix audit — 2026-09-20

This pass is based on the full project that already contains the archive/Apktool reconstruction and the in-tree `mt-data-files-provider` module.

## Fixed CI errors

- Removed every explicit `androidx.compose.foundation.layout.weight` import. `Modifier.weight()` is now resolved through the active `RowScope`/`ColumnScope`, avoiding Kotlin resolving the internal `RowColumnParentData.weight` property.
- Added the missing `kotlinx.coroutines.launch` import to `ApkInfoDialogs.kt`; `withContext` now executes inside the remembered coroutine scope.
- Added the missing Compose `getValue` delegate import to `BuiltInOpenDialogs.kt`, fixing the `State<ExplorerPrefs>` delegation cascade and its `sortedBy` type-inference errors.
- Added missing `android.provider.DocumentsContract` and `android.provider.OpenableColumns` imports to `ExplorerScreen.kt`.
- Corrected `return@forEach` to `return@forEachIndexed` in the move loop in `ExplorerViewModel.kt`.
- Removed the same problematic explicit `weight` import from `FileContextMenuDialog.kt` and `ExplorerPreferencesDialog.kt`.
- Media player does not access `PlayerView.showBuffering`; it uses a compatible setter bridge instead.
- Image/media screens no longer depend on an un-opted Material3 experimental top app bar.
- The current `CodeEditorScreen.kt` contains real save/search/replace/undo/redo handlers and no invalid `UiText...show` expressions.

## Similar-error audit

Project-wide checks cover:

- Compose scope-extension imports (`weight`, `align`, `matchParentSize`)
- Compose delegated state (`getValue` / `setValue`)
- coroutine `launch` imports for remembered coroutine scopes
- Android provider symbols (`DocumentsContract`, `OpenableColumns`)
- invalid lambda labels
- private Media3 field access
- experimental Compose APIs without an opt-in in the affected screens
- XML and TOML parse validity
- balanced Kotlin/Java source delimiters

## Previously requested exclusions restored

- Removed the obsolete Root/Shell startup preferences from `ExplorerPrefs`, persistence, and the Preferences screen.
- No Network section is present in the explorer drawer.
- Add local storage remains inside the drawer three-dot menu.
- Key & Certificate Manager remains a direct drawer tool.

## Gradle profiles / provider module

`:mt-data-files-provider` remains an in-tree Android library and follows the same project properties used by both wrappers:

- `mtapktool.javaVersion`
- `mtapktool.compileSdk`
- `mtapktool.minSdk`

`gradlew17` passes `-Pmtapktool.javaVersion=17`; `gradlew25` passes `-Pmtapktool.javaVersion=25`.

## Validation note

The container does not contain the Gradle distribution/Android dependency cache needed for a real `:app:compileDebugKotlin` run, and external download access is unavailable. The source/resource/static checks above were therefore performed locally, including targeted Kotlin front-end checks of the files from the reported CI failure.
