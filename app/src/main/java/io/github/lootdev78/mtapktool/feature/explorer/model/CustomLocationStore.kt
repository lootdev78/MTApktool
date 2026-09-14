package io.github.lootdev78.mtapktool.feature.explorer.model

import android.content.Context
import android.net.Uri

/** Persisted SAF document-tree locations added from the explorer or settings. */
object CustomLocationStore {
    private const val PREFS = "custom_document_locations"
    private const val KEY_URIS = "tree_uris"

    fun all(context: Context): List<Uri> = context.applicationContext
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .getStringSet(KEY_URIS, emptySet())
        .orEmpty()
        .mapNotNull { runCatching { Uri.parse(it) }.getOrNull() }
        .sortedBy { displayName(it).lowercase() }

    fun add(context: Context, uri: Uri) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val values = prefs.getStringSet(KEY_URIS, emptySet()).orEmpty().toMutableSet()
        values += uri.toString()
        prefs.edit().putStringSet(KEY_URIS, values).apply()
    }

    fun remove(context: Context, uri: Uri) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val values = prefs.getStringSet(KEY_URIS, emptySet()).orEmpty().toMutableSet()
        values -= uri.toString()
        prefs.edit().putStringSet(KEY_URIS, values).apply()
    }

    fun displayName(uri: Uri): String {
        val id = runCatching { android.provider.DocumentsContract.getTreeDocumentId(uri) }.getOrNull().orEmpty()
        return id.substringAfter(':', id).trim('/').substringAfterLast('/').ifBlank { uri.authority ?: "Location" }
    }
}
