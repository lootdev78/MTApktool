package io.github.lootdev78.mtapktool.feature.explorer.util

import android.content.Context
import android.content.Intent
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File
import modder.hub.editor.MainActivity as TextEditorActivity

object FileOpener {

    private val editableExtensions = setOf(
        "txt", "xml", "json", "json5", "yaml", "yml", "properties", "gradle", "kts",
        "kt", "java", "smali", "md", "html", "htm", "css", "js", "ts", "sh", "bat",
        "ini", "cfg", "conf", "toml", "csv", "log", "pro", "rules", "aidl"
    )

    fun openFile(
        context: Context,
        file: File
    ) {
        val extension = file.extension.lowercase()

        if (extension in editableExtensions) {
            try {
                context.startActivity(
                    Intent(context, TextEditorActivity::class.java)
                        .putExtra("path", file.absolutePath)
                )
                return
            } catch (_: Exception) {
                // Fall through to Android's external chooser if the embedded editor cannot open.
            }
        }

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