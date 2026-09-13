package io.github.lootdev78.mtapktool.archive

import android.content.Context

data class ArchiveDefaults(
    val format: ArchiveFormat = ArchiveFormat.ZIP,
    val level: ArchiveLevel = ArchiveLevel.NORMAL,
    val splitLengthMb: Long = 0,
    val compressEachIndependently: Boolean = false,
    val deleteSourcesAfterCompression: Boolean = false,
    val compressToOtherPane: Boolean = false,
)

object ArchiveSettings {
    private const val PREFS = "archive_defaults"

    fun load(context: Context): ArchiveDefaults {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return ArchiveDefaults(
            format = ArchiveFormat.fromLabel(p.getString("format", ArchiveFormat.ZIP.label)),
            level = ArchiveLevel.fromLabel(p.getString("level", ArchiveLevel.NORMAL.label)),
            splitLengthMb = p.getLong("split_mb", 0L).coerceAtLeast(0L),
            compressEachIndependently = p.getBoolean("each", false),
            deleteSourcesAfterCompression = p.getBoolean("delete", false),
            compressToOtherPane = p.getBoolean("other_pane", false),
        )
    }

    fun save(context: Context, value: ArchiveDefaults) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("format", value.format.label)
            .putString("level", value.level.label)
            .putLong("split_mb", value.splitLengthMb)
            .putBoolean("each", value.compressEachIndependently)
            .putBoolean("delete", value.deleteSourcesAfterCompression)
            .putBoolean("other_pane", value.compressToOtherPane)
            .apply()
    }
}
