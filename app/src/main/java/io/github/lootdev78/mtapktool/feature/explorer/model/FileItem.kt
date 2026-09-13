package io.github.lootdev78.mtapktool.feature.explorer.model

import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class FileItem(
    val file: File
) {
    val name: String = file.name
    val path: String = file.path
    val isDirectory: Boolean = file.isDirectory

    // Formats last modified date to match "YY-MM-DD HH:mm" (e.g. 22-05-16 12:36)
    val formattedDate: String by lazy {
        val sdf = SimpleDateFormat("yy-MM-dd HH:mm", Locale.getDefault())
        sdf.format(Date(file.lastModified()))
    }

    // Formats byte size (B, KB, MB, GB)
    val sizeText: String by lazy {
        if (isDirectory) "" else formatFileSize(file.length())
    }

    private fun formatFileSize(size: Long): String {
        if (size <= 0) return "0B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt()
        return String.format(Locale.US, "%.0f%s", size / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
    }
}