package io.github.lootdev78.mtapktool.apktool

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Small persistent history so jobs/results remain visible when the UI/activity is recreated. */
object ApktoolJobHistory {
    private const val PREFS = "mtapktool_job_history"
    private const val KEY = "jobs"
    private const val MAX_ITEMS = 50

    @Synchronized
    fun save(context: Context, job: ApktoolJobInfo) {
        val jobs = load(context).toMutableList()
        jobs.removeAll { it.id == job.id }
        jobs += job
        val trimmed = jobs.sortedByDescending { it.createdAt }.take(MAX_ITEMS)
        val array = JSONArray()
        trimmed.forEach { item ->
            array.put(JSONObject().apply {
                put("id", item.id)
                put("title", item.title)
                put("command", item.command)
                put("status", item.status)
                put("line", item.line)
                put("output", item.output ?: JSONObject.NULL)
                put("createdAt", item.createdAt)
            })
        }
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, array.toString()).apply()
    }

    @Synchronized
    fun load(context: Context): List<ApktoolJobInfo> {
        val raw = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    val output = if (item.isNull("output")) null else item.optString("output").takeIf { it.isNotBlank() }
                    add(
                        ApktoolJobInfo(
                            id = item.optString("id"),
                            title = item.optString("title"),
                            command = item.optString("command"),
                            status = item.optString("status"),
                            line = item.optString("line"),
                            output = output,
                            createdAt = item.optLong("createdAt", 0L),
                        ),
                    )
                }
            }.filter { it.id.isNotBlank() }.sortedByDescending { it.createdAt }
        }.getOrDefault(emptyList())
    }
}
