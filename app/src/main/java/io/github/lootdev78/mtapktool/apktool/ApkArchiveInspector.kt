package io.github.lootdev78.mtapktool.apktool

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

/** Lightweight APK structure inspection used by the decompile dialog. */
object ApkArchiveInspector {
    data class DexInfo(
        val name: String,
        val version: String,
        val fileSize: Long,
        val strings: Long,
        val types: Long,
        val protos: Long,
        val fields: Long,
        val methods: Long,
        val classes: Long,
        val checksum: String,
    )

    data class ResourceInfo(
        val hasManifest: Boolean,
        val resourcesArscSize: Long,
        val resFiles: Int,
        val resSize: Long,
        val assetFiles: Int,
        val assetSize: Long,
        val nativeLibFiles: Int,
        val nativeLibSize: Long,
    )

    data class Summary(
        val apkName: String,
        val dexFiles: List<DexInfo>,
        val resources: ResourceInfo,
    )

    fun inspect(apk: File): Summary {
        if (!apk.isFile) throw IOException("APK not found: $apk")
        return inspectZip(apk, apk.name)
    }

    /**
     * Inspect an APK nested in APKS/APKM/XAPK without keeping a persistent extracted copy.
     */
    fun inspectSplitArchive(archive: File, entryPath: String?, cacheDir: File): Summary {
        if (!SplitArchiveSupport.isSplitArchive(archive)) {
            return inspect(archive)
        }
        val selected = entryPath
            ?: SplitArchiveSupport.inspect(archive).firstOrNull { it.preferred }?.path
            ?: SplitArchiveSupport.inspect(archive).firstOrNull()?.path
            ?: throw IOException("No APK found in ${archive.name}")

        if (!cacheDir.isDirectory && !cacheDir.mkdirs()) {
            throw IOException("Cannot create cache directory: $cacheDir")
        }
        val temp = File.createTempFile("mtapktool-inspect-", ".apk", cacheDir)
        try {
            ZipFile(archive).use { outer ->
                val entry = outer.getEntry(selected) ?: throw IOException("APK entry not found: $selected")
                outer.getInputStream(entry).use { input ->
                    FileOutputStream(temp).use { output -> input.copyTo(output, 128 * 1024) }
                }
            }
            return inspectZip(temp, File(selected).name)
        } finally {
            temp.delete()
        }
    }

    private fun inspectZip(file: File, displayName: String): Summary {
        val dex = mutableListOf<DexInfo>()
        var hasManifest = false
        var arscSize = 0L
        var resFiles = 0
        var resSize = 0L
        var assetFiles = 0
        var assetSize = 0L
        var libFiles = 0
        var libSize = 0L

        ZipFile(file).use { zip ->
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                if (entry.isDirectory) continue
                val name = entry.name
                val lower = name.lowercase(Locale.ROOT)
                when {
                    lower == "androidmanifest.xml" -> hasManifest = true
                    lower == "resources.arsc" -> arscSize = positiveSize(entry)
                    lower.startsWith("res/") -> {
                        resFiles++
                        resSize += positiveSize(entry)
                    }
                    lower.startsWith("assets/") -> {
                        assetFiles++
                        assetSize += positiveSize(entry)
                    }
                    lower.startsWith("lib/") -> {
                        libFiles++
                        libSize += positiveSize(entry)
                    }
                }
                if (DEX_NAME.matches(File(name).name)) {
                    zip.getInputStream(entry).use { input ->
                        val header = ByteArray(112)
                        var off = 0
                        while (off < header.size) {
                            val n = input.read(header, off, header.size - off)
                            if (n < 0) break
                            off += n
                        }
                        if (off >= 112 && header[0] == 'd'.code.toByte() && header[1] == 'e'.code.toByte() && header[2] == 'x'.code.toByte()) {
                            dex += parseDex(name, header, entry)
                        }
                    }
                }
            }
        }

        return Summary(
            apkName = displayName,
            dexFiles = dex.sortedWith(compareBy<DexInfo> { dexIndex(it.name) }.thenBy { it.name }),
            resources = ResourceInfo(
                hasManifest = hasManifest,
                resourcesArscSize = arscSize,
                resFiles = resFiles,
                resSize = resSize,
                assetFiles = assetFiles,
                assetSize = assetSize,
                nativeLibFiles = libFiles,
                nativeLibSize = libSize,
            ),
        )
    }

    private fun parseDex(name: String, header: ByteArray, entry: ZipEntry): DexInfo {
        val version = buildString {
            for (i in 4..6) {
                val c = header[i].toInt().toChar()
                if (c.isDigit()) append(c)
            }
        }.ifBlank { "?" }
        val headerFileSize = u32(header, 32)
        return DexInfo(
            name = name,
            version = version,
            fileSize = if (headerFileSize > 0) headerFileSize else positiveSize(entry),
            strings = u32(header, 56),
            types = u32(header, 64),
            protos = u32(header, 72),
            fields = u32(header, 80),
            methods = u32(header, 88),
            classes = u32(header, 96),
            checksum = "0x" + u32(header, 8).toString(16).padStart(8, '0'),
        )
    }

    private fun u32(data: ByteArray, offset: Int): Long =
        (data[offset].toLong() and 0xffL) or
            ((data[offset + 1].toLong() and 0xffL) shl 8) or
            ((data[offset + 2].toLong() and 0xffL) shl 16) or
            ((data[offset + 3].toLong() and 0xffL) shl 24)

    private fun positiveSize(entry: ZipEntry): Long = entry.size.coerceAtLeast(0L)

    private fun dexIndex(name: String): Int {
        val base = File(name).name.lowercase(Locale.ROOT)
        if (base == "classes.dex") return 1
        return base.removePrefix("classes").removeSuffix(".dex").toIntOrNull() ?: Int.MAX_VALUE
    }

    private val DEX_NAME = Regex("(?i)^classes(?:\\d+)?\\.dex$")
}
