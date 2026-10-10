package io.github.lootdev78.mtapktool.feature.explorer.state

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class Bookmark(val path: String, val name: String = BookmarkStore.defaultName(path))

/** Keeps named entries and migrates the previous path-only bookmarks without losing them. */
object BookmarkStore {
    private fun preferences(context: Context) = context.getSharedPreferences("explorer_bookmarks", Context.MODE_PRIVATE)

    fun load(context: Context): List<Bookmark> {
        val prefs = preferences(context)
        val stored = prefs.getString("entries", null)
        val entries = stored?.let { text -> runCatching {
            val array = JSONArray(text)
            List(array.length()) { index ->
                val item = array.getJSONObject(index)
                val path = item.getString("path")
                Bookmark(path, item.optString("name").ifBlank { defaultName(path) })
            }
        }.getOrNull() } ?: prefs.getStringSet("paths", emptySet()).orEmpty().sorted().map { Bookmark(it) }
        return entries.filter { it.path.isNotBlank() }.distinctBy { it.path }
    }

    fun save(context: Context, entries: List<Bookmark>) {
        val unique = entries.distinctBy { it.path }
        val array = JSONArray()
        unique.forEach { entry -> array.put(JSONObject().put("path", entry.path).put("name", entry.name)) }
        preferences(context).edit().putString("entries", array.toString())
            .putStringSet("paths", unique.map { it.path }.toSet()).apply()
    }

    fun normalizePath(value: String): String {
        val path = value.trim()
        require(path.isNotBlank()) { "Einen Pfad angeben" }
        if (path.startsWith("content://", ignoreCase = true)) {
            val uri = Uri.parse(path).normalizeScheme()
            require(!uri.authority.isNullOrBlank()) { "Ungültiger Dokumentpfad" }
            return uri.toString()
        }
        require(File(path).isAbsolute) { "Einen absoluten Pfad oder eine content://-Adresse angeben" }
        return File(path).normalize().path
    }

    fun defaultName(path: String): String = if (path.startsWith("content://")) {
        Uri.decode(path.substringAfterLast('/')).substringAfterLast(':').ifBlank { "Dokumente" }
    } else File(path).name.ifBlank { path }
}
