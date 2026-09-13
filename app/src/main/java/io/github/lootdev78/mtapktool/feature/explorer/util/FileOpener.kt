package io.github.lootdev78.mtapktool.feature.explorer.util

import android.content.Context
import android.content.Intent
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File

object FileOpener {

    fun openFile(
        context: Context,
        file: File
    ) {
        val extension = file.extension.lowercase()

        val mimeType =
            MimeTypeMap
                .getSingleton()
                .getMimeTypeFromExtension(extension)
                ?: "*/*"

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        try {
            context.startActivity(
                Intent.createChooser(intent, "Open with")
            )
        } catch (e: Exception) {
            Toast.makeText(
                context,
                "No app found to open this file",
                Toast.LENGTH_SHORT
            ).show()
        }
    }
}