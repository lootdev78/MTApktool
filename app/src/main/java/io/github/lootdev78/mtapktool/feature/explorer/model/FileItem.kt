package io.github.lootdev78.mtapktool.feature.explorer.model

import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * One explorer row. Normal filesystem rows use [file] directly. SAF rows keep their tree-scoped
 * document URI in [safUri]; [file] is only a lightweight name placeholder in that case.
 */
data class FileItem(
    val file: File,
    val safUri: String? = null,
    val remoteUri: String? = null,
    val displayName: String? = null,
    val directoryOverride: Boolean? = null,
    val sizeOverride: Long? = null,
    val modifiedOverride: Long? = null,
    val mimeType: String? = null,
) {
    val isFtp: Boolean get() = remoteUri != null
    val isSaf: Boolean get() = safUri != null
    val name: String = displayName ?: file.name
    val path: String = remoteUri ?: safUri ?: file.path
    val isDirectory: Boolean = directoryOverride ?: file.isDirectory
    val modifiedAt: Long = modifiedOverride ?: file.lastModified()
    val fileSize: Long = sizeOverride ?: if (isDirectory) 0L else file.length()
    val extensionName: String = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
    val unixMode: Int? = if (isSaf || isFtp) null else runCatching { android.system.Os.lstat(file.absolutePath).st_mode and 0xFFF }.getOrNull()

    val formattedDate: String by lazy {
        val sdf = SimpleDateFormat("yy-MM-dd HH:mm", Locale.getDefault())
        if (modifiedAt > 0L) sdf.format(Date(modifiedAt)) else ""
    }

    val sizeText: String by lazy {
        if (isDirectory) "" else formatFileSize(fileSize)
    }

    private fun formatFileSize(size: Long): String {
        if (size <= 0) return "0B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt().coerceIn(0, units.lastIndex)
        return String.format(Locale.US, "%.0f%s", size / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
    }
}
