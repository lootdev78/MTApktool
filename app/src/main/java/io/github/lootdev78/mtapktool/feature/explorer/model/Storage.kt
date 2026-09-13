package io.github.lootdev78.mtapktool.feature.explorer.model

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager
import androidx.annotation.RequiresApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Android
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.SdCard
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.core.content.ContextCompat
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


@RequiresApi(Build.VERSION_CODES.R)
private fun getAndroid11StorageRoots(
    context: Context
): List<File> {
    val storageManager =
        context.getSystemService(Context.STORAGE_SERVICE) as StorageManager

    return storageManager.storageVolumes
        .mapNotNull { it.directory }
        .distinctBy { it.absolutePath }
}

fun getStorageRoots(context: Context): List<StorageInfo> {
    val result = mutableListOf<StorageInfo>()

    val root = File("/")

    result += StorageInfo(
        name = "Root Directory",
        path = "/",
        file = root,
        icon = Icons.Outlined.PhoneAndroid
    )

    val internalStorage = Environment.getExternalStorageDirectory()

    result += StorageInfo(
        name = "Internal Storage",
        path = internalStorage.absolutePath,
        file = internalStorage,
        icon = Icons.Outlined.SdCard
    )

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {

        getAndroid11StorageRoots(context)
            .filter {
                it.absolutePath != internalStorage.absolutePath &&
                        it.absolutePath != "/"
            }
            .forEach { directory ->
                result += StorageInfo(
                    name = directory.name,
                    path = directory.absolutePath,
                    file = directory,
                    icon = Icons.Outlined.SdCard
                )
            }

    } else {

        // Android 10 fallback
        ContextCompat.getExternalFilesDirs(context, null)
            .mapNotNull { appDir ->
                appDir
                    ?.parentFile
                    ?.parentFile
                    ?.parentFile
                    ?.parentFile
            }
            .filter {
                it.absolutePath != internalStorage.absolutePath
            }
            .distinctBy {
                it.absolutePath
            }
            .forEach { directory ->
                result += StorageInfo(
                    name = directory.name,
                    path = directory.absolutePath,
                    file = directory,
                    icon = Icons.Outlined.SdCard
                )
            }
    }

    return result
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
