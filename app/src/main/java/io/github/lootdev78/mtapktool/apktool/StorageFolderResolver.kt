package io.github.lootdev78.mtapktool.apktool

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager
import android.provider.DocumentsContract
import java.io.File

/** Converts folders selected by ACTION_OPEN_DOCUMENT_TREE into raw paths used by Apktool's File API. */
object StorageFolderResolver {
    fun persistPermission(context: Context, uri: Uri) {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        runCatching { context.contentResolver.takePersistableUriPermission(uri, flags) }
    }

    fun resolveTreeUri(context: Context, uri: Uri): File? {
        if (uri.scheme.equals("file", ignoreCase = true)) return uri.path?.let(::File)
        if (!DocumentsContract.isTreeUri(uri)) return null

        val authority = uri.authority.orEmpty()
        if (authority != "com.android.externalstorage.documents") return null

        val docId = runCatching { DocumentsContract.getTreeDocumentId(uri) }.getOrNull() ?: return null
        val parts = docId.split(':', limit = 2)
        val volumeId = parts.firstOrNull().orEmpty()
        val relative = parts.getOrNull(1).orEmpty().trimStart('/')

        val base = when {
            volumeId.equals("primary", ignoreCase = true) -> Environment.getExternalStorageDirectory()
            Build.VERSION.SDK_INT >= 30 -> {
                val manager = context.getSystemService(StorageManager::class.java)
                manager.storageVolumes.firstOrNull { volume ->
                    volume.uuid?.equals(volumeId, ignoreCase = true) == true
                }?.directory ?: File("/storage/$volumeId")
            }
            else -> File("/storage/$volumeId")
        }
        return if (relative.isBlank()) base else File(base, relative)
    }

    fun ensureOutputDirectory(file: File): Result<File> = runCatching {
        val canonical = file.canonicalFile
        if (canonical.exists() && !canonical.isDirectory) {
            error("Ausgabepfad ist keine Ordner: ${canonical.absolutePath}")
        }
        if (!canonical.exists() && !canonical.mkdirs()) {
            error("Ausgabeordner kann nicht erstellt werden: ${canonical.absolutePath}")
        }
        if (!canonical.canWrite()) {
            error("Keine Schreibberechtigung für: ${canonical.absolutePath}")
        }
        canonical
    }
}
