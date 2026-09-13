package io.github.lootdev78.mtapktool.archive

import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.model.ZipParameters
import net.lingala.zip4j.model.enums.AesKeyStrength
import net.lingala.zip4j.model.enums.CompressionLevel
import net.lingala.zip4j.model.enums.CompressionMethod
import net.lingala.zip4j.model.enums.EncryptionMethod
import org.apache.commons.compress.archivers.sevenz.SevenZMethod
import org.apache.commons.compress.archivers.sevenz.SevenZOutputFile
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorOutputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream
import org.apache.commons.compress.compressors.gzip.GzipParameters
import org.apache.commons.compress.compressors.lz4.FramedLZ4CompressorOutputStream
import org.apache.commons.compress.compressors.xz.XZCompressorOutputStream
import org.apache.commons.compress.compressors.zstandard.ZstdCompressorOutputStream
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.nio.file.Files

object ArchiveEngine {
    fun create(request: ArchiveRequest): List<File> {
        require(request.sources.isNotEmpty()) { "No source files selected" }
        request.sources.forEach { require(it.exists()) { "Source does not exist: ${it.absolutePath}" } }
        if (!request.outputDirectory.isDirectory && !request.outputDirectory.mkdirs()) {
            throw IOException("Cannot create output directory: ${request.outputDirectory}")
        }
        if (request.password.isNotBlank() && request.format !in setOf(ArchiveFormat.ZIP, ArchiveFormat.SEVEN_Z)) {
            throw IOException("Password encryption is available for ZIP and 7z archives")
        }

        val outputs = if (request.compressEachIndependently) {
            request.sources.flatMap { source ->
                val requested = if (request.sources.size == 1) request.fileName else source.nameWithoutExtension.ifBlank { source.name }
                createSingle(listOf(source), request.copy(sources = listOf(source), fileName = requested))
            }
        } else {
            createSingle(request.sources, request)
        }

        if (request.deleteSourcesAfterCompression) {
            request.sources.forEach { source ->
                if (Files.isSymbolicLink(source.toPath())) source.delete()
                else if (!source.deleteRecursively() && source.exists()) throw IOException("Cannot delete source: $source")
            }
        }
        return outputs
    }

    private fun createSingle(sources: List<File>, request: ArchiveRequest): List<File> {
        if ((request.format == ArchiveFormat.GZIP || request.format == ArchiveFormat.XZ) &&
            (sources.size != 1 || !sources.single().isFile)
        ) {
            throw IOException("${request.format.label} can compress exactly one regular file. Use tar.${if (request.format == ArchiveFormat.GZIP) "gz" else "xz"} for folders or multiple files.")
        }

        val output = uniqueFile(request.outputDirectory, normalizedFileName(request.fileName, request.format))
        when (request.format) {
            ArchiveFormat.ZIP -> createZip(output, sources, request.level, request.password)
            ArchiveFormat.SEVEN_Z -> create7z(output, sources, request.level, request.password)
            ArchiveFormat.TAR -> createTar(output, sources) { it }
            ArchiveFormat.TAR_GZ -> createTar(output, sources) { gzipStream(it, request.level) }
            ArchiveFormat.TAR_XZ -> createTar(output, sources) { XZCompressorOutputStream(it, request.level.preset()) }
            ArchiveFormat.TAR_ZST -> createTar(output, sources) { ZstdCompressorOutputStream(it, request.level.preset()) }
            ArchiveFormat.TAR_BZ2 -> createTar(output, sources) { BZip2CompressorOutputStream(it, request.level.bzipBlockSize()) }
            ArchiveFormat.TAR_LZ4 -> createTar(output, sources) { FramedLZ4CompressorOutputStream(it) }
            ArchiveFormat.GZIP -> compressSingle(output, sources.single()) { gzipStream(it, request.level) }
            ArchiveFormat.XZ -> compressSingle(output, sources.single()) { XZCompressorOutputStream(it, request.level.preset()) }
        }
        if (!output.isFile || output.length() == 0L) throw IOException("Archive was not created: $output")
        return if (request.splitLengthBytes > 0L && output.length() > request.splitLengthBytes) {
            splitFile(output, request.splitLengthBytes)
        } else listOf(output)
    }

    private fun createZip(output: File, sources: List<File>, level: ArchiveLevel, password: String) {
        val zip = if (password.isBlank()) ZipFile(output) else ZipFile(output, password.toCharArray())
        val parameters = ZipParameters().apply {
            compressionMethod = if (level == ArchiveLevel.STORE) CompressionMethod.STORE else CompressionMethod.DEFLATE
            if (compressionMethod == CompressionMethod.DEFLATE) compressionLevel = level.toZipLevel()
            if (password.isNotBlank()) {
                isEncryptFiles = true
                encryptionMethod = EncryptionMethod.AES
                aesKeyStrength = AesKeyStrength.KEY_STRENGTH_256
            }
        }
        sources.forEach { source ->
            val p = ZipParameters(parameters)
            if (level == ArchiveLevel.APK_MODE && source.isFile && source.extension.lowercase() in alreadyCompressedExtensions) {
                p.compressionMethod = CompressionMethod.STORE
            }
            if (source.isDirectory) zip.addFolder(source, p) else zip.addFile(source, p)
        }
    }

    private fun ArchiveLevel.toZipLevel(): CompressionLevel = when (this) {
        ArchiveLevel.FASTEST -> CompressionLevel.FASTEST
        ArchiveLevel.FAST -> CompressionLevel.FAST
        ArchiveLevel.MAXIMUM -> CompressionLevel.MAXIMUM
        ArchiveLevel.ULTRA -> CompressionLevel.ULTRA
        ArchiveLevel.STORE -> CompressionLevel.NORMAL
        ArchiveLevel.NORMAL, ArchiveLevel.APK_MODE -> CompressionLevel.NORMAL
    }

    private fun create7z(output: File, sources: List<File>, level: ArchiveLevel, password: String) {
        val writer = if (password.isBlank()) SevenZOutputFile(output) else SevenZOutputFile(output, password.toCharArray())
        writer.use { seven ->
            seven.setContentCompression(if (level == ArchiveLevel.STORE) SevenZMethod.COPY else SevenZMethod.LZMA2)
            sources.forEach { source -> addTo7z(seven, source.parentFile ?: source, source) }
        }
    }

    private fun addTo7z(seven: SevenZOutputFile, base: File, file: File) {
        if (Files.isSymbolicLink(file.toPath())) return
        val name = file.relativeTo(base).path.replace(File.separatorChar, '/').let {
            if (file.isDirectory) it.trimEnd('/') + "/" else it
        }
        val entry = seven.createArchiveEntry(file, name)
        seven.putArchiveEntry(entry)
        if (file.isFile) BufferedInputStream(FileInputStream(file)).use { input -> input.copyTo(seven) }
        seven.closeArchiveEntry()
        if (file.isDirectory) file.listFiles()?.sortedBy { it.name.lowercase() }?.forEach { addTo7z(seven, base, it) }
    }

    private fun createTar(output: File, sources: List<File>, wrapper: (OutputStream) -> OutputStream) {
        BufferedOutputStream(FileOutputStream(output)).use { fileOut ->
            wrapper(fileOut).use { compressed ->
                TarArchiveOutputStream(compressed).use { tar ->
                    tar.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX)
                    tar.setBigNumberMode(TarArchiveOutputStream.BIGNUMBER_POSIX)
                    sources.forEach { source -> addToTar(tar, source.parentFile ?: source, source) }
                    tar.finish()
                }
            }
        }
    }

    private fun addToTar(tar: TarArchiveOutputStream, base: File, file: File) {
        if (Files.isSymbolicLink(file.toPath())) return
        val name = file.relativeTo(base).path.replace(File.separatorChar, '/').let {
            if (file.isDirectory) it.trimEnd('/') + "/" else it
        }
        val entry = TarArchiveEntry(file, name)
        tar.putArchiveEntry(entry)
        if (file.isFile) BufferedInputStream(FileInputStream(file)).use { input -> input.copyTo(tar) }
        tar.closeArchiveEntry()
        if (file.isDirectory) file.listFiles()?.sortedBy { it.name.lowercase() }?.forEach { addToTar(tar, base, it) }
    }

    private fun compressSingle(output: File, source: File, wrapper: (OutputStream) -> OutputStream) {
        BufferedInputStream(FileInputStream(source)).use { input ->
            BufferedOutputStream(FileOutputStream(output)).use { fileOut ->
                wrapper(fileOut).use { compressed -> input.copyTo(compressed) }
            }
        }
    }

    private fun splitFile(output: File, partSize: Long): List<File> {
        require(partSize > 0) { "Split size must be greater than zero" }
        val parts = mutableListOf<File>()
        BufferedInputStream(FileInputStream(output)).use { input ->
            val buffer = ByteArray(1024 * 1024)
            var index = 1
            var eof = false
            while (!eof) {
                val part = File(output.parentFile, "%s.%03d".format(output.name, index++))
                var written = 0L
                BufferedOutputStream(FileOutputStream(part)).use { out ->
                    while (written < partSize) {
                        val max = minOf(buffer.size.toLong(), partSize - written).toInt()
                        val read = input.read(buffer, 0, max)
                        if (read < 0) { eof = true; break }
                        if (read == 0) continue
                        out.write(buffer, 0, read)
                        written += read
                    }
                }
                if (part.length() == 0L) part.delete() else parts += part
            }
        }
        if (parts.size > 1) {
            if (!output.delete()) throw IOException("Cannot remove unsplit archive: $output")
            return parts
        }
        parts.forEach { it.delete() }
        return listOf(output)
    }


    private fun gzipStream(out: OutputStream, level: ArchiveLevel): GzipCompressorOutputStream {
        val parameters = GzipParameters().apply { compressionLevel = level.preset() }
        return GzipCompressorOutputStream(out, parameters)
    }

    private fun ArchiveLevel.preset(): Int = when (this) {
        ArchiveLevel.STORE -> 0
        ArchiveLevel.FASTEST -> 1
        ArchiveLevel.FAST -> 3
        ArchiveLevel.NORMAL, ArchiveLevel.APK_MODE -> 6
        ArchiveLevel.MAXIMUM -> 8
        ArchiveLevel.ULTRA -> 9
    }

    private fun ArchiveLevel.bzipBlockSize(): Int = preset().coerceIn(1, 9)

    private fun normalizedFileName(value: String, format: ArchiveFormat): String {
        val clean = value.trim().ifBlank { "Archive${format.extension}" }
        val known = ArchiveFormat.entries.any { clean.lowercase().endsWith(it.extension) }
        return if (known) clean else clean + format.extension
    }

    private fun uniqueFile(parent: File, name: String): File {
        var out = File(parent, name)
        if (!out.exists()) return out
        val dot = name.indexOf('.')
        val base = if (dot > 0) name.substring(0, dot) else name
        val suffix = if (dot > 0) name.substring(dot) else ""
        var i = 1
        while (out.exists()) out = File(parent, "$base-$i${suffix}").also { i++ }
        return out
    }

    private val alreadyCompressedExtensions = setOf(
        "png", "jpg", "jpeg", "webp", "gif", "mp3", "ogg", "mp4", "m4a", "aac", "zip", "7z", "gz", "xz", "bz2", "zst", "apk", "apks", "xapk", "apkm"
    )
}
