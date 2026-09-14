package io.github.lootdev78.mtapktool.feature.explorer.model

import android.content.Context
import io.github.lootdev78.mtapktool.feature.explorer.state.FileFilter
import io.github.lootdev78.mtapktool.feature.explorer.state.SortField
import io.github.lootdev78.mtapktool.feature.explorer.state.SortSpec
import io.github.lootdev78.mtapktool.feature.explorer.viewmodel.ActivePane

/** Small persistent store for the explorer UI options shown in the MT-style overflow menus. */
object ExplorerPreferenceStore {
    private const val PREFS = "explorer_options_v2"
    private const val SEP = '\u001f'

    data class Snapshot(
        val leftSystemHidden: Boolean,
        val rightSystemHidden: Boolean,
        val leftManualHidden: Boolean,
        val rightManualHidden: Boolean,
        val manualHiddenPaths: Set<String>,
        val leftSort: SortSpec,
        val rightSort: SortSpec,
        val leftFilter: FileFilter,
        val rightFilter: FileFilter,
        val folderSortOverrides: Map<String, SortSpec>,
    )

    fun load(context: Context): Snapshot {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        fun sort(prefix: String): SortSpec {
            val field = runCatching { SortField.valueOf(p.getString("${prefix}_sort_field", SortField.NAME.name)!!) }.getOrDefault(SortField.NAME)
            return SortSpec(field, p.getBoolean("${prefix}_sort_desc", false))
        }
        fun filter(prefix: String): FileFilter = runCatching {
            FileFilter.valueOf(p.getString("${prefix}_filter", FileFilter.ALL.name)!!)
        }.getOrDefault(FileFilter.ALL)
        val overrides = p.getStringSet("folder_sort_overrides", emptySet()).orEmpty().mapNotNull { raw ->
            val parts = raw.split(SEP, limit = 4)
            if (parts.size != 4) return@mapNotNull null
            val pane = runCatching { ActivePane.valueOf(parts[0]) }.getOrNull() ?: return@mapNotNull null
            val field = runCatching { SortField.valueOf(parts[1]) }.getOrNull() ?: return@mapNotNull null
            val desc = parts[2].toBooleanStrictOrNull() ?: false
            "${pane.name}:${parts[3]}" to SortSpec(field, desc)
        }.toMap()
        return Snapshot(
            leftSystemHidden = p.getBoolean("left_system_hidden", true),
            rightSystemHidden = p.getBoolean("right_system_hidden", true),
            leftManualHidden = p.getBoolean("left_manual_hidden", true),
            rightManualHidden = p.getBoolean("right_manual_hidden", true),
            manualHiddenPaths = p.getStringSet("manual_hidden_paths", emptySet()).orEmpty(),
            leftSort = sort("left"),
            rightSort = sort("right"),
            leftFilter = filter("left"),
            rightFilter = filter("right"),
            folderSortOverrides = overrides,
        )
    }

    fun saveVisibility(context: Context, pane: ActivePane, systemHidden: Boolean? = null, manualHidden: Boolean? = null) {
        val prefix = pane.name.lowercase()
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().apply {
            systemHidden?.let { putBoolean("${prefix}_system_hidden", it) }
            manualHidden?.let { putBoolean("${prefix}_manual_hidden", it) }
        }.apply()
    }

    fun saveManualHiddenPaths(context: Context, paths: Set<String>) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putStringSet("manual_hidden_paths", paths).apply()
    }

    fun saveFilter(context: Context, pane: ActivePane, filter: FileFilter) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("${pane.name.lowercase()}_filter", filter.name).apply()
    }

    fun saveDefaultSort(context: Context, pane: ActivePane, spec: SortSpec) {
        val prefix = pane.name.lowercase()
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("${prefix}_sort_field", spec.field.name)
            .putBoolean("${prefix}_sort_desc", spec.descending)
            .apply()
    }

    fun saveFolderSortOverrides(context: Context, overrides: Map<String, SortSpec>) {
        val encoded = overrides.mapNotNull { (key, spec) ->
            val split = key.indexOf(':')
            if (split <= 0 || split >= key.lastIndex) return@mapNotNull null
            val pane = key.substring(0, split)
            val path = key.substring(split + 1)
            "$pane$SEP${spec.field.name}$SEP${spec.descending}$SEP$path"
        }.toSet()
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putStringSet("folder_sort_overrides", encoded).apply()
    }
}
