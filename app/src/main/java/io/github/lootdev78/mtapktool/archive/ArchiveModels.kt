package io.github.lootdev78.mtapktool.archive

import java.io.File

enum class ArchiveFormat(val label: String, val extension: String) {
    ZIP("zip", ".zip"),
    SEVEN_Z("7z", ".7z"),
    TAR("tar", ".tar"),
    TAR_GZ("tar.gz", ".tar.gz"),
    TAR_XZ("tar.xz", ".tar.xz"),
    TAR_ZST("tar.zst", ".tar.zst"),
    TAR_BZ2("tar.bz2", ".tar.bz2"),
    TAR_LZ4("tar.lz4", ".tar.lz4"),
    GZIP("gzip", ".gz"),
    XZ("xz", ".xz"),
    RAR("rar (read-only)", ".rar");

    companion object {
        fun fromLabel(value: String?): ArchiveFormat = entries.firstOrNull { it.label == value } ?: ZIP

        /** Longest suffix wins so .tar.gz is not mistaken for plain .gz. */
        fun fromFile(file: File): ArchiveFormat? {
            val name = file.name.lowercase()
            if (name.endsWith(".apk") || name.endsWith(".jar")) return ZIP
            return entries.sortedByDescending { it.extension.length }
                .firstOrNull { name.endsWith(it.extension) }
        }
    }
}

enum class ArchiveLevel(val label: String) {
    STORE("Store"),
    FASTEST("Fastest"),
    FAST("Fast"),
    NORMAL("Normal"),
    MAXIMUM("Maximum"),
    ULTRA("Ultra"),
    APK_MODE("APK mode");

    companion object {
        fun fromLabel(value: String?): ArchiveLevel = entries.firstOrNull { it.label == value } ?: NORMAL
    }
}

data class ArchiveRequest(
    val sources: List<File>,
    val outputDirectory: File,
    val fileName: String,
    val format: ArchiveFormat,
    val level: ArchiveLevel,
    val password: String = "",
    val splitLengthBytes: Long = 0L,
    val compressEachIndependently: Boolean = false,
    val deleteSourcesAfterCompression: Boolean = false,
)

data class ArchiveExtractRequest(
    val archive: File,
    val outputDirectory: File,
    val password: String = "",
    val deleteSourceAfterExtraction: Boolean = false,
)
