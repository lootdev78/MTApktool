package io.github.lootdev78.mtapktool.feature.explorer.util

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.core.content.FileProvider
import io.github.lootdev78.mtapktool.core.i18n.UiText
import java.io.File

object FileOpener {
    fun mimeFor(name: String): String {
        val extension = name.substringAfterLast('.', "").lowercase()
        return when (extension) {
            "dex" -> "application/vnd.android.dex"
            "smali" -> "text/plain"
            "arsc" -> "application/octet-stream"
            "apk" -> "application/vnd.android.package-archive"
            "7z" -> "application/x-7z-compressed"
            "rar" -> "application/vnd.rar"
            "zst", "zstd" -> "application/zstd"
            "lz4" -> "application/x-lz4"
            else -> MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: "application/octet-stream"
        }
    }

    fun openFile(context: Context, file: File) {
        try {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            openUri(context, uri, file.name, mimeFor(file.name))
        } catch (error: IllegalArgumentException) {
            Toast.makeText(context, UiText.t("File cannot be shared from this location", "Datei kann von diesem Speicherort nicht geteilt werden"), Toast.LENGTH_SHORT).show()
        }
    }

    fun openUri(context: Context, uri: Uri, displayName: String, mimeType: String? = null) {
        val resolvedMime = mimeType ?: mimeFor(displayName)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, resolvedMime)
            clipData = ClipData.newRawUri(displayName, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        }
        try {
            context.startActivity(Intent.createChooser(intent, "Open with"))
        } catch (_: Exception) {
            Toast.makeText(context, UiText.t("No app found to open this file", "Keine App zum Öffnen dieser Datei gefunden"), Toast.LENGTH_SHORT).show()
        }
    }
}
