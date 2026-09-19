package io.github.lootdev78.mtapktool.feature.explorer.saf

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.io.IOException

/** Persisted user-added Storage Access Framework trees. */
data class CustomLocation(
    val id: String,
    val name: String,
    val treeUri: String,
    val hidden: Boolean = false,
) {
    val uri: Uri get() = Uri.parse(treeUri)
}

object CustomLocationStore {
    private const val PREFS = "mtapktool_custom_locations"
    private const val KEY = "locations"

    fun load(context: Context): List<CustomLocation> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "[]") ?: "[]"
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val o = array.optJSONObject(i) ?: continue
                    val uri = o.optString("uri")
                    if (uri.isBlank()) continue
                    add(CustomLocation(
                        id = o.optString("id").ifBlank { UUID.randomUUID().toString() },
                        name = o.optString("name").ifBlank { defaultName(context, Uri.parse(uri)) },
                        treeUri = uri,
                        hidden = o.optBoolean("hidden", false),
                    ))
                }
            }
        }.getOrDefault(emptyList())
    }

    fun add(context: Context, uri: Uri, name: String? = null): CustomLocation {
        takePermission(context, uri)
        val items = load(context).toMutableList()
        items.indexOfFirst { it.treeUri == uri.toString() }.takeIf { it >= 0 }?.let { index ->
            val existing = items[index]
            val restored = existing.copy(hidden = false)
            if (restored != existing) {
                items[index] = restored
                save(context, items)
            }
            return restored
        }
        val location = CustomLocation(UUID.randomUUID().toString(), name?.trim().orEmpty().ifBlank { defaultName(context, uri) }, uri.toString(), false)
        items += location
        save(context, items)
        return location
    }

    fun rename(context: Context, id: String, name: String) {
        val value = name.trim()
        if (value.isBlank()) return
        save(context, load(context).map { if (it.id == id) it.copy(name = value) else it })
    }

    fun setHidden(context: Context, id: String, hidden: Boolean) {
        save(context, load(context).map { if (it.id == id) it.copy(hidden = hidden) else it })
    }

    fun move(context: Context, id: String, delta: Int) {
        if (delta == 0) return
        val items = load(context).toMutableList()
        val from = items.indexOfFirst { it.id == id }
        if (from < 0) return
        val to = (from + delta).coerceIn(0, items.lastIndex)
        if (to == from) return
        val item = items.removeAt(from)
        items.add(to, item)
        save(context, items)
    }

    fun remove(context: Context, id: String) {
        val current = load(context)
        val target = current.firstOrNull { it.id == id }
        if (target != null) {
            runCatching {
                context.contentResolver.releasePersistableUriPermission(
                    target.uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
        }
        save(context, current.filterNot { it.id == id })
    }

    fun rootDocumentUri(location: CustomLocation): Uri = SafFileSystem.rootDocumentUri(location.uri)

    private fun takePermission(context: Context, uri: Uri) {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        context.contentResolver.takePersistableUriPermission(uri, flags)
        val persisted = context.contentResolver.persistedUriPermissions.firstOrNull { it.uri == uri }
        if (persisted?.isWritePermission != true || !SafFileSystem.isWritableTree(context, uri)) {
            runCatching {
                context.contentResolver.releasePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
            throw IOException("Der ausgewählte Speicherort gewährt keinen Schreibzugriff")
        }
    }

    private fun save(context: Context, items: List<CustomLocation>) {
        val array = JSONArray()
        items.forEach { item ->
            array.put(JSONObject().put("id", item.id).put("name", item.name).put("uri", item.treeUri).put("hidden", item.hidden))
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, array.toString()).apply()
    }

    private fun defaultName(context: Context, uri: Uri): String {
        val root = SafFileSystem.rootDocumentUri(uri)
        return SafFileSystem.documentName(context, root)
            ?: runCatching { DocumentsContract.getTreeDocumentId(uri).substringAfterLast(':') }.getOrNull()
            ?.ifBlank { null }
            ?: "Storage"
    }
}
