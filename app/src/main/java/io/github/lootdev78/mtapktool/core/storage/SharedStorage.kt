package io.github.lootdev78.mtapktool.core.storage

import android.content.Context
import android.os.Build
import android.os.storage.StorageManager
import java.io.File

/** Modern shared-storage root discovery without the deprecated external-storage-directory API. */
object SharedStorage {
    private val fallbackPrimary = File("/storage/emulated/0")

    fun primaryRoot(context: Context? = null): File {
        val env = System.getenv("EXTERNAL_STORAGE")
            ?.takeIf { it.isNotBlank() }
            ?.let(::File)
        if (env != null) return env

        if (context != null) {
            roots(context).firstOrNull { it.absolutePath.startsWith("/storage/emulated/") }?.let { return it }
            deriveVolumeRoot(context.getExternalFilesDir(null))?.let { return it }
        }
        return fallbackPrimary
    }

    fun roots(context: Context): List<File> {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val manager = context.getSystemService(Context.STORAGE_SERVICE) as StorageManager
            val volumes = manager.storageVolumes.mapNotNull { it.directory }
            if (volumes.isNotEmpty()) return volumes.distinctBy { it.absolutePath }
        }
        return context.getExternalFilesDirs(null)
            .mapNotNull(::deriveVolumeRoot)
            .distinctBy { it.absolutePath }
    }

    /** `/storage/<volume>/Android/data/<pkg>/files` -> `/storage/<volume>`. */
    private fun deriveVolumeRoot(appFiles: File?): File? {
        var cursor = appFiles ?: return null
        repeat(4) { cursor = cursor.parentFile ?: return null }
        return cursor
    }
}
