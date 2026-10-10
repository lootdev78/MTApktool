package io.github.lootdev78.mtapktool.feature.explorer.util

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.MediaStore
import java.io.File

data class LocatedUri(val localFile: File? = null, val parentDocumentUri: String? = null)

/** Resolves only locations Android exposes to this app; never treats a cache copy as the original. */
object ExternalUriLocator {
    fun locate(context: Context, uri: Uri): LocatedUri? {
        if (uri.scheme == "file") return uri.path?.let(::File)?.takeIf { it.exists() }?.let { LocatedUri(localFile = it) }
        if (uri.authority == "${context.packageName}.fileprovider") {
            val segments = uri.pathSegments
            val root = when (segments.firstOrNull()) {
                "external_files" -> android.os.Environment.getExternalStorageDirectory()
                "temporary_files" -> context.cacheDir
                "ftp_workspaces" -> File(context.filesDir, "ftp-editor")
                else -> null
            }
            if (root != null) {
                val file = File(root, segments.drop(1).joinToString("/")).canonicalFile
                if ((file == root.canonicalFile || file.path.startsWith(root.canonicalPath + "/")) && file.exists()) return LocatedUri(localFile = file)
            }
        }
        val documentId = runCatching { DocumentsContract.getDocumentId(uri) }.getOrNull()
        if (documentId != null) {
            val file = when {
                uri.authority == "com.android.externalstorage.documents" -> {
                    val parts = documentId.split(':', limit = 2)
                    val root = if (parts[0].equals("primary", true)) android.os.Environment.getExternalStorageDirectory() else File("/storage", parts[0])
                    File(root, parts.getOrElse(1) { "" })
                }
                uri.authority == "com.android.providers.downloads.documents" && documentId.startsWith("raw:") -> File(documentId.removePrefix("raw:"))
                else -> null
            }
            if (file != null && file.exists()) return LocatedUri(localFile = file)
            val trees = context.contentResolver.persistedUriPermissions.filter { it.isReadPermission && it.uri.authority == uri.authority && DocumentsContract.isTreeUri(it.uri) }.map { it.uri }
            for (tree in trees) {
                val scoped = DocumentsContract.buildDocumentUriUsingTree(tree, documentId)
                val path = runCatching { DocumentsContract.findDocumentPath(context.contentResolver, scoped)?.path }.getOrNull()
                val parent = path?.dropLast(1)?.lastOrNull() ?: continue
                return LocatedUri(parentDocumentUri = DocumentsContract.buildDocumentUriUsingTree(tree, parent).toString())
            }
        }
        if (uri.authority == "media") runCatching {
            @Suppress("DEPRECATION") val column = MediaStore.MediaColumns.DATA
            context.contentResolver.query(uri, arrayOf(column), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(column)
                    if (index >= 0 && !cursor.isNull(index)) File(cursor.getString(index)).takeIf { it.exists() }?.let { return LocatedUri(localFile = it) }
                }
            }
        }
        return null
    }
}
