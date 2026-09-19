package io.github.lootdev78.mtapktool.archive

import java.io.File

enum class ArchiveFormat(
    val label: String,
    val extension: String,
    val canCreate: Boolean = true,
    val canUpdate: Boolean = canCreate,
) {
    ZIP("zip", ".zip"),
    SEVEN_Z("7z", ".7z"),
    RAR("rar", ".rar", canCreate = false, canUpdate = false),
    TAR("tar", ".tar"),
    TAR_GZ("tar.gz", ".tar.gz"),
    TAR_XZ("tar.xz", ".tar.xz"),
    TAR_ZST("tar.zst", ".tar.zst"),
    TAR_BZ2("tar.bz2", ".tar.bz2"),
    TAR_LZ4("tar.lz4", ".tar.lz4"),
    GZIP("gzip", ".gz"),
    XZ("xz", ".xz"),
    BZIP2("bzip2", ".bz2"),
    ZSTD("zstd", ".zst"),
    LZ4("lz4", ".lz4");

    companion object {
        fun fromLabel(value: String?): ArchiveFormat = entries.firstOrNull { it.label == value } ?: ZIP

        /** Longest suffix / common alias wins. APK/JAR/split packages are ZIP containers. */
        fun fromFile(file: File): ArchiveFormat? {
            val name = file.name.lowercase()
            if (listOf(".apk", ".jar", ".apks", ".apkm", ".xapk", ".apkx", ".ipa").any(name::endsWith)) return ZIP
            return when {
                name.endsWith(".tgz") -> TAR_GZ
                name.endsWith(".txz") -> TAR_XZ
                name.endsWith(".tzst") || name.endsWith(".tzstd") -> TAR_ZST
                name.endsWith(".tbz") || name.endsWith(".tbz2") -> TAR_BZ2
                name.endsWith(".tlz4") -> TAR_LZ4
                else -> entries.sortedByDescending { it.extension.length }.firstOrNull { name.endsWith(it.extension) }
            }
        }

        val creatable: List<ArchiveFormat> get() = entries.filter { it.canCreate }
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
