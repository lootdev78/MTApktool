package io.github.lootdev78.mtapktool.apktool

import android.content.Context

data class ApkModuleOptions(
    val zipAlignment: Int = 4,
    val sharedLibraryAlignment: Int = 16 * 1024,
    val zipForce: Boolean = true,
    val zipVerify: Boolean = true,
    val autoSelectDeviceSplits: Boolean = true,
    val includeOptionalSplits: Boolean = true,
    val includeFeatureSplits: Boolean = true,
    val keepExtractedSplits: Boolean = false,
    val cleanMetaInf: Boolean = true,
    val antiSplitForceMerge: Boolean = false,
    val antiSplitStripMetadata: Boolean = true,
    val compressionLevel: Int = 6,
)

object ApkModulePreferences {
    private const val PREFS = "apk_modules"

    fun load(context: Context): ApkModuleOptions {
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return ApkModuleOptions(
            zipAlignment = p.getInt("zipalign_alignment", 4).validPowerOfTwo(4),
            sharedLibraryAlignment = p.getInt("zipalign_so_alignment", 16 * 1024).let { if (it == 0) 0 else it.validPowerOfTwo(16 * 1024) },
            zipForce = p.getBoolean("zipalign_force", true),
            zipVerify = p.getBoolean("zipalign_verify", true),
            autoSelectDeviceSplits = p.getBoolean("antisplit_auto_device", true),
            includeOptionalSplits = p.getBoolean("antisplit_optional", true),
            includeFeatureSplits = p.getBoolean("antisplit_features", true),
            keepExtractedSplits = p.getBoolean("antisplit_keep_splits", false),
            cleanMetaInf = p.getBoolean("antisplit_clean_meta", true),
            antiSplitForceMerge = p.getBoolean("antisplit_force_merge", false),
            antiSplitStripMetadata = p.getBoolean("antisplit_strip_metadata", true),
            compressionLevel = p.getInt("antisplit_compression", 6).coerceIn(0, 9),
        )
    }

    fun save(context: Context, value: ApkModuleOptions) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putInt("zipalign_alignment", value.zipAlignment.validPowerOfTwo(4))
            .putInt("zipalign_so_alignment", if (value.sharedLibraryAlignment == 0) 0 else value.sharedLibraryAlignment.validPowerOfTwo(16 * 1024))
            .putBoolean("zipalign_force", value.zipForce)
            .putBoolean("zipalign_verify", value.zipVerify)
            .putBoolean("antisplit_auto_device", value.autoSelectDeviceSplits)
            .putBoolean("antisplit_optional", value.includeOptionalSplits)
            .putBoolean("antisplit_features", value.includeFeatureSplits)
            .putBoolean("antisplit_keep_splits", value.keepExtractedSplits)
            .putBoolean("antisplit_clean_meta", value.cleanMetaInf)
            .putBoolean("antisplit_force_merge", value.antiSplitForceMerge)
            .putBoolean("antisplit_strip_metadata", value.antiSplitStripMetadata)
            .putInt("antisplit_compression", value.compressionLevel.coerceIn(0, 9))
            .apply()
    }

    private fun Int.validPowerOfTwo(fallback: Int): Int =
        if (this > 0 && (this and (this - 1)) == 0) this else fallback
}
