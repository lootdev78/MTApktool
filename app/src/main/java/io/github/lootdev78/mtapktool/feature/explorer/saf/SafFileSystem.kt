package io.github.lootdev78.mtapktool.feature.explorer.saf

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import io.github.lootdev78.mtapktool.feature.explorer.model.FileItem
import io.github.lootdev78.mtapktool.settings.ExplorerPreferences
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException

object SafFileSystem {
    private val projection = arrayOf(
        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
        DocumentsContract.Document.COLUMN_MIME_TYPE,
        DocumentsContract.Document.COLUMN_SIZE,
        DocumentsContract.Document.COLUMN_LAST_MODIFIED,
        DocumentsContract.Document.COLUMN_FLAGS,
    )

    fun isSafPath(path: String): Boolean = path.startsWith("content://", ignoreCase = true)

    fun rootDocumentUri(treeUri: Uri): Uri = DocumentsContract.buildDocumentUriUsingTree(
        treeUri,
        DocumentsContract.getTreeDocumentId(treeUri),
    )

    fun list(context: Context, directoryUri: Uri): List<FileItem> {
        val resolver = context.contentResolver
        val documentId = DocumentsContract.getDocumentId(directoryUri)
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(directoryUri, documentId)
        val result = ArrayList<FileItem>()
        resolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
            val idIdx = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameIdx = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val mimeIdx = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
            val sizeIdx = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_SIZE)
            val modifiedIdx = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
            while (cursor.moveToNext()) {
                val id = cursor.getString(idIdx)
                val name = cursor.getString(nameIdx) ?: id.substringAfterLast('/')
                val mime = cursor.getString(mimeIdx)
                val isDir = mime == DocumentsContract.Document.MIME_TYPE_DIR
                val size = if (cursor.isNull(sizeIdx)) 0L else cursor.getLong(sizeIdx)
                val modified = if (cursor.isNull(modifiedIdx)) 0L else cursor.getLong(modifiedIdx)
                val child = DocumentsContract.buildDocumentUriUsingTree(directoryUri, id)
                result += FileItem(
                    file = File(name),
                    safUri = child.toString(),
                    displayName = name,
                    directoryOverride = isDir,
                    sizeOverride = if (isDir) 0L else size,
                    modifiedOverride = modified,
                    mimeType = mime,
                )
            }
        }
        return result
    }

    fun isWritableTree(context: Context, treeUri: Uri): Boolean {
        val root = rootDocumentUri(treeUri)
        return context.contentResolver.query(
            root,
            arrayOf(DocumentsContract.Document.COLUMN_FLAGS),
            null,
            null,
            null,
        )?.use { cursor ->
            if (!cursor.moveToFirst()) return@use false
            val flags = cursor.getInt(0)
            val canCreate = flags and DocumentsContract.Document.FLAG_DIR_SUPPORTS_CREATE != 0
            val canWrite = flags and DocumentsContract.Document.FLAG_SUPPORTS_WRITE != 0
            canCreate || canWrite
        } ?: false
    }

    fun documentName(context: Context, uri: Uri): String? = context.contentResolver.query(
        uri,
        arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
        null,
        null,
        null,
    )?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }

    fun mimeType(context: Context, uri: Uri): String? = context.contentResolver.getType(uri)


    fun findChildByName(context: Context, directoryUri: Uri, name: String): Uri? =
        list(context, directoryUri).firstOrNull { it.name == name }?.path?.let(Uri::parse)

    fun uniqueChildName(context: Context, directoryUri: Uri, desiredName: String): String {
        if (findChildByName(context, directoryUri, desiredName) == null) return desiredName
        val dot = desiredName.lastIndexOf('.')
        val base = if (dot > 0) desiredName.substring(0, dot) else desiredName
        val ext = if (dot > 0) desiredName.substring(dot) else ""
        var i = 1
        var candidate: String
        do {
            candidate = "$base ($i)$ext"
            i++
        } while (findChildByName(context, directoryUri, candidate) != null)
        return candidate
    }

    fun create(context: Context, parentUri: Uri, name: String, directory: Boolean): Uri {
        val mime = if (directory) DocumentsContract.Document.MIME_TYPE_DIR else guessMime(name)
        return DocumentsContract.createDocument(context.contentResolver, parentUri, mime, name)
            ?: throw IOException("Could not create $name")
    }

    fun rename(context: Context, uri: Uri, newName: String): Uri {
        return DocumentsContract.renameDocument(context.contentResolver, uri, newName)
            ?: throw IOException("Could not rename document")
    }

    fun delete(context: Context, uri: Uri) {
        if (!DocumentsContract.deleteDocument(context.contentResolver, uri)) {
            throw IOException("Could not delete document")
        }
    }

    fun copyFileToSaf(context: Context, source: File, targetDirectory: Uri, targetName: String = source.name): Uri {
        val target = create(context, targetDirectory, targetName, false)
        context.contentResolver.openOutputStream(target, "w")?.use { out ->
            FileInputStream(source).use { input -> input.copyTo(out, transferBufferSize(context)) }
        } ?: throw IOException("Could not open destination")
        return target
    }

    fun copyDirectoryToSaf(context: Context, source: File, targetDirectory: Uri, targetName: String = source.name): Uri {
        val target = create(context, targetDirectory, targetName, true)
        source.listFiles()?.forEach { child ->
            if (child.isDirectory) copyDirectoryToSaf(context, child, target)
            else copyFileToSaf(context, child, target)
        }
        return target
    }

    fun copySafToFileSystem(context: Context, source: Uri, sourceName: String, isDirectory: Boolean, targetDirectory: File, targetName: String = sourceName): File {
        val target = File(targetDirectory, targetName)
        if (isDirectory) {
            if (!target.exists() && !target.mkdirs()) throw IOException("Could not create ${target.path}")
            list(context, source).forEach { child ->
                copySafToFileSystem(context, Uri.parse(child.path), child.name, child.isDirectory, target)
            }
        } else {
            target.parentFile?.mkdirs()
            context.contentResolver.openInputStream(source)?.use { input ->
                FileOutputStream(target).use { out -> input.copyTo(out, transferBufferSize(context)) }
            } ?: throw IOException("Could not read $sourceName")
        }
        return target
    }

    fun copySafToSaf(context: Context, source: Uri, sourceName: String, isDirectory: Boolean, targetDirectory: Uri, targetName: String = sourceName): Uri {
        val target = create(context, targetDirectory, targetName, isDirectory)
        if (isDirectory) {
            list(context, source).forEach { child ->
                copySafToSaf(context, Uri.parse(child.path), child.name, child.isDirectory, target)
            }
        } else {
            context.contentResolver.openInputStream(source)?.use { input ->
                context.contentResolver.openOutputStream(target, "w")?.use { out -> input.copyTo(out, transferBufferSize(context)) }
                    ?: throw IOException("Could not open destination")
            } ?: throw IOException("Could not read $sourceName")
        }
        return target
    }

    fun materializeToCache(context: Context, source: Uri, displayName: String): File {
        val dir = File(context.cacheDir, "saf-open").apply { mkdirs() }
        val out = File(dir, displayName)
        context.contentResolver.openInputStream(source)?.use { input ->
            FileOutputStream(out).use { output -> input.copyTo(output, transferBufferSize(context)) }
        } ?: throw IOException("Could not open $displayName")
        return out
    }

    private fun transferBufferSize(context: Context): Int =
        if (ExplorerPreferences.current(context).optimizeExternalTransfer) 4 * 1024 * 1024 else 256 * 1024

    private fun guessMime(name: String): String = android.webkit.MimeTypeMap.getSingleton()
        .getMimeTypeFromExtension(name.substringAfterLast('.', "").lowercase())
        ?: "application/octet-stream"
}
