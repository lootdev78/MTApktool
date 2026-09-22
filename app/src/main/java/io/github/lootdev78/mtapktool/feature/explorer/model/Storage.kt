package io.github.lootdev78.mtapktool.feature.explorer.model

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Android
import androidx.compose.material.icons.outlined.SdCard
import androidx.compose.ui.graphics.vector.ImageVector
import io.github.lootdev78.mtapktool.core.storage.SharedStorage
import java.io.File

data class StorageInfo(
    val name: String,
    val path: String,
    val file: File,
    val icon: ImageVector,
) {
    val totalSpace: Long
        get() = file.totalSpace

    val freeSpace: Long
        get() = file.freeSpace

    val usedSpace: Long
        get() = totalSpace - freeSpace

    val usedPercentage: Float
        get() = if (totalSpace > 0) {
            usedSpace.toFloat() / totalSpace
        } else {
            0f
        }
}


fun getStorageRoots(context: Context): List<StorageInfo> {
    val result = mutableListOf<StorageInfo>()

    val internalStorage = SharedStorage.primaryRoot(context)
    result += StorageInfo(
        name = "Internal Storage",
        path = internalStorage.absolutePath,
        file = internalStorage,
        icon = Icons.Outlined.SdCard,
    )

    SharedStorage.roots(context)
        .filter { it.absolutePath != internalStorage.absolutePath && it.absolutePath != "/" }
        .forEach { directory ->
            result += StorageInfo(
                name = directory.name.ifBlank { directory.absolutePath },
                path = directory.absolutePath,
                file = directory,
                icon = Icons.Outlined.SdCard,
            )
        }

    return result.distinctBy { it.path }
}


fun formatSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"

    val kb = bytes / 1024.0
    if (kb < 1024) return "%.1f KB".format(kb)

    val mb = kb / 1024
    if (mb < 1024) return "%.1f MB".format(mb)

    val gb = mb / 1024
    if (gb < 1024) return "%.2f GB".format(gb)

    val tb = gb / 1024
    return "%.2f TB".format(tb)
}
