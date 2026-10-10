package io.github.lootdev78.mtapktool.apktool

import io.github.lootdev78.mtapktool.apkeditor.ApkEditorEngine
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

enum class ApkEditorAction(val title: String, val suffix: String, val engine: ApkEditorEngine.Action) {
    KILL_SIGNATURE("Kill signature verification", "_killed", ApkEditorEngine.Action.KILL_SIGNATURE),
    REFACTOR("Refactor obfuscated resource names", "_refactored", ApkEditorEngine.Action.REFACTOR),
    OPTIMIZE("Optimize APK", "_optimized", ApkEditorEngine.Action.OPTIMIZE),
    PROTECT("Protect APK (REAndroid APKEditor)", "_protected", ApkEditorEngine.Action.PROTECT),
}

data class ApkEditorRequest(
    val action: ApkEditorAction,
    val input: String,
    val output: String,
    val overwrite: Boolean = false,
    val killMethod: String = "MT",
    val publicXml: String = "",
    val fixTypes: Boolean = true,
    val cleanMeta: Boolean = true,
    val skipManifest: Boolean = false,
    val confuseZip: Boolean = false,
    val dexLevel: Int = 0,
    val deepOptimize: Boolean = false,
    val preserveDebug: Boolean = true,
    val maxPasses: Int = 5,
    val deletePatterns: List<String> = emptyList(),
) {
    fun engineOptions() = ApkEditorEngine.Options().also {
        it.action = action.engine; it.killMethod = killMethod
        it.publicXml = publicXml.takeIf(String::isNotBlank)?.let(::File)
        it.fixTypeNames = fixTypes; it.cleanMeta = cleanMeta
        it.skipManifest = skipManifest; it.confuseZip = confuseZip; it.dexLevel = dexLevel
        it.deepOptimize = deepOptimize; it.preserveDebug = preserveDebug; it.maxPasses = maxPasses
        it.deletePatterns.addAll(deletePatterns)
    }
    fun toJson(): String = JSONObject().apply {
        put("action", action.name); put("input", input); put("output", output); put("overwrite", overwrite)
        put("killMethod", killMethod); put("publicXml", publicXml); put("fixTypes", fixTypes); put("cleanMeta", cleanMeta)
        put("skipManifest", skipManifest); put("confuseZip", confuseZip); put("dexLevel", dexLevel)
        put("deepOptimize", deepOptimize); put("preserveDebug", preserveDebug); put("maxPasses", maxPasses)
        put("deletePatterns", JSONArray(deletePatterns))
    }.toString()

    companion object {
        fun fromJson(json: String): ApkEditorRequest {
            val value = JSONObject(json)
            val patterns = value.optJSONArray("deletePatterns") ?: JSONArray()
            return ApkEditorRequest(
                ApkEditorAction.valueOf(value.getString("action")), value.getString("input"), value.getString("output"),
                value.optBoolean("overwrite"), value.optString("killMethod", "MT"), value.optString("publicXml"),
                value.optBoolean("fixTypes", true), value.optBoolean("cleanMeta", true),
                value.optBoolean("skipManifest"), value.optBoolean("confuseZip"), value.optInt("dexLevel").coerceIn(0, 1),
                value.optBoolean("deepOptimize"), value.optBoolean("preserveDebug", true), value.optInt("maxPasses", 5).coerceIn(1, 25),
                List(patterns.length()) { patterns.getString(it) },
            )
        }
    }
}
