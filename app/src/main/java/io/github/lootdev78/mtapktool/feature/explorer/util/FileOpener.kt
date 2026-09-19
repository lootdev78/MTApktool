package io.github.lootdev78.mtapktool.feature.explorer.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File

object FileOpener {

    fun openFile(context: Context, file: File) {
        val extension = file.extension.lowercase()
        val mimeType = when (extension) {
            "dex" -> "application/vnd.android.dex"
            else -> MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: "application/octet-stream"
        }

        // Archive entries live in cacheDir. URI creation must be part of the guarded block too:
        // FileProvider throws IllegalArgumentException when a path is not covered by file_paths.xml.
        try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file,
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mimeType)
                clipData = android.content.ClipData.newRawUri(file.name, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Open with"))
        } catch (error: IllegalArgumentException) {
            Toast.makeText(
                context,
                "File cannot be shared from this location: ${error.message.orEmpty()}",
                Toast.LENGTH_SHORT,
            ).show()
        } catch (_: Exception) {
            Toast.makeText(context, "No app found to open this file", Toast.LENGTH_SHORT).show()
        }
    }

    fun openUri(context: Context, uri: Uri, displayName: String, mimeType: String? = null) {
        val extension = displayName.substringAfterLast('.', "").lowercase()
        val resolvedMime = mimeType ?: when (extension) {
            "dex" -> "application/vnd.android.dex"
            else -> MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: "application/octet-stream"
        }
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, resolvedMime)
            clipData = android.content.ClipData.newRawUri(displayName, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        }
        try {
            context.startActivity(Intent.createChooser(intent, "Open with"))
        } catch (_: Exception) {
            Toast.makeText(context, "No app found to open this file", Toast.LENGTH_SHORT).show()
        }
    }
}
