package io.github.lootdev78.mtapktool.tools

import android.content.Context
import io.github.lootdev78.mtapktool.apkextractor.ApkExtractorEngine
import java.io.File

data class ApkExtractorOptions(
    val outputRoot: String,
    val includeSystemApps: Boolean,
    val defaultSplitMode: String,
    val compressionLevel: Int,
    val sortMode: String = "name",
    val showIcon: Boolean = true,
    val showAppName: Boolean = true,
    val showPackageName: Boolean = true,
    val showVersionName: Boolean = true,
    val showVersionCode: Boolean = true,
    val showFirstInstall: Boolean = true,
    val showLastUpdate: Boolean = true,
    val showExtractIcon: Boolean = false,
    val showExtractResources: Boolean = false,
    val showExtractDex: Boolean = false,
    val showExtractManifest: Boolean = false,
    val showExtractBase: Boolean = true,
    val showExtractSplit: Boolean = true,
    val showExtractLibs: Boolean = false,
)

object ApkExtractorPreferences {
    private const val PREFS = "mtapktool_apkextractor"
    fun load(context: Context): ApkExtractorOptions {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return ApkExtractorOptions(
            outputRoot = p.getString("outputRoot", null)?.takeIf { it.isNotBlank() }
                ?: ApkExtractorEngine.defaultOutputRoot().absolutePath,
            includeSystemApps = p.getBoolean("includeSystemApps", false),
            defaultSplitMode = p.getString("defaultSplitMode", "apks")?.takeIf { it == "apks" || it == "merge" } ?: "apks",
            compressionLevel = p.getInt("compressionLevel", 6).coerceIn(0, 9),
            sortMode = p.getString("sortMode", "name")?.takeIf { it in setOf("name", "last_update", "first_install") } ?: "name",
            showIcon = p.getBoolean("showIcon", true),
            showAppName = p.getBoolean("showAppName", true),
            showPackageName = p.getBoolean("showPackageName", true),
            showVersionName = p.getBoolean("showVersionName", true),
            showVersionCode = p.getBoolean("showVersionCode", true),
            showFirstInstall = p.getBoolean("showFirstInstall", true),
            showLastUpdate = p.getBoolean("showLastUpdate", true),
            showExtractIcon = p.getBoolean("showExtractIcon", false),
            showExtractResources = p.getBoolean("showExtractResources", false),
            showExtractDex = p.getBoolean("showExtractDex", false),
            showExtractManifest = p.getBoolean("showExtractManifest", false),
            showExtractBase = p.getBoolean("showExtractBase", true),
            showExtractSplit = p.getBoolean("showExtractSplit", true),
            showExtractLibs = p.getBoolean("showExtractLibs", false),
        )
    }
    fun save(context: Context, value: ApkExtractorOptions) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("outputRoot", value.outputRoot)
            .putBoolean("includeSystemApps", value.includeSystemApps)
            .putString("defaultSplitMode", value.defaultSplitMode)
            .putInt("compressionLevel", value.compressionLevel.coerceIn(0, 9))
            .putString("sortMode", value.sortMode.takeIf { it in setOf("name", "last_update", "first_install") } ?: "name")
            .putBoolean("showIcon", value.showIcon)
            .putBoolean("showAppName", value.showAppName)
            .putBoolean("showPackageName", value.showPackageName)
            .putBoolean("showVersionName", value.showVersionName)
            .putBoolean("showVersionCode", value.showVersionCode)
            .putBoolean("showFirstInstall", value.showFirstInstall)
            .putBoolean("showLastUpdate", value.showLastUpdate)
            .putBoolean("showExtractIcon", value.showExtractIcon)
            .putBoolean("showExtractResources", value.showExtractResources)
            .putBoolean("showExtractDex", value.showExtractDex)
            .putBoolean("showExtractManifest", value.showExtractManifest)
            .putBoolean("showExtractBase", value.showExtractBase)
            .putBoolean("showExtractSplit", value.showExtractSplit)
            .putBoolean("showExtractLibs", value.showExtractLibs)
            .apply()
    }
}

data class ApkClonerOptions(
    val suffix: String,
    val outputSameFolder: Boolean,
)

object ApkClonerPreferences {
    private const val PREFS = "mtapktool_apkcloner"
    fun load(context: Context): ApkClonerOptions {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return ApkClonerOptions(
            suffix = p.getString("suffix", "_clone")?.ifBlank { "_clone" } ?: "_clone",
            outputSameFolder = p.getBoolean("sameFolder", true),
        )
    }
    fun save(context: Context, value: ApkClonerOptions) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("suffix", value.suffix)
            .putBoolean("sameFolder", value.outputSameFolder)
            .apply()
    }

    fun defaultOutput(input: File, suffix: String): File {
        val parent = input.parentFile ?: File(".")
        return File(parent, input.nameWithoutExtension + suffix + ".apk")
    }
}
