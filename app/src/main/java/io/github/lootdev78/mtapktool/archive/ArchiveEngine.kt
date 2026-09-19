package io.github.lootdev78.mtapktool.archive

import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.model.ZipParameters
import net.lingala.zip4j.model.enums.AesKeyStrength
import net.lingala.zip4j.model.enums.CompressionLevel
import net.lingala.zip4j.model.enums.CompressionMethod
import net.lingala.zip4j.model.enums.EncryptionMethod
import org.apache.commons.compress.archivers.sevenz.SevenZFile
import org.apache.commons.compress.archivers.sevenz.SevenZMethod
import org.apache.commons.compress.archivers.sevenz.SevenZOutputFile
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorOutputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream
import org.apache.commons.compress.compressors.gzip.GzipParameters
import org.apache.commons.compress.compressors.lz4.FramedLZ4CompressorInputStream
import org.apache.commons.compress.compressors.lz4.FramedLZ4CompressorOutputStream
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream
import org.apache.commons.compress.compressors.xz.XZCompressorOutputStream
import org.apache.commons.compress.compressors.zstandard.ZstdCompressorInputStream
import org.apache.commons.compress.compressors.zstandard.ZstdCompressorOutputStream
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

object ArchiveEngine {
    fun supports(file: File): Boolean = file.isFile && ArchiveFormat.fromFile(file) != null

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

    /** Extracts a supported archive into [ArchiveExtractRequest.outputDirectory]. */
    fun extract(request: ArchiveExtractRequest): File {
        val archive = request.archive
        require(archive.isFile) { "Archive does not exist: ${archive.absolutePath}" }
        val destination = request.outputDirectory
        if (!destination.exists() && !destination.mkdirs()) {
            throw IOException("Cannot create extraction directory: ${destination.absolutePath}")
        }
        if (!destination.isDirectory) throw IOException("Extraction target is not a directory: ${destination.absolutePath}")

        extractToDirectory(archive, destination, request.password)
        if (request.deleteSourceAfterExtraction && !archive.delete()) {
            throw IOException("Archive extracted, but source could not be deleted: ${archive.absolutePath}")
        }
        return destination
    }

    /**
     * Used by the explorer's temporary archive workspace. Existing contents are replaced.
     * [onProgress] is intentionally lightweight and reports 0..100 so the owning pane can
     * be frozen with an MT-style loading indicator until the archive is actually browsable.
     */
    fun extractToDirectory(
        archive: File,
        destination: File,
        password: String = "",
        onProgress: (Int) -> Unit = {},
    ) {
        val format = ArchiveFormat.fromFile(archive)
            ?: throw IOException("Unsupported archive format: ${archive.name}")
        if (destination.exists() && !destination.isDirectory) {
            throw IOException("Extraction target is not a directory: ${destination.absolutePath}")
        }
        if (!destination.exists() && !destination.mkdirs()) {
            throw IOException("Cannot create extraction target: ${destination.absolutePath}")
        }

        onProgress(0)
        when (format) {
            ArchiveFormat.ZIP -> extractZip(archive, destination, password, onProgress)
            ArchiveFormat.SEVEN_Z -> extract7z(archive, destination, password, onProgress)
            ArchiveFormat.TAR,
            ArchiveFormat.TAR_GZ,
            ArchiveFormat.TAR_XZ,
            ArchiveFormat.TAR_ZST,
            ArchiveFormat.TAR_BZ2,
            ArchiveFormat.TAR_LZ4 -> extractTar(archive, destination, format, onProgress)
            ArchiveFormat.GZIP -> extractSingleCompressed(archive, destination, ArchiveFormat.GZIP, onProgress)
            ArchiveFormat.XZ -> extractSingleCompressed(archive, destination, ArchiveFormat.XZ, onProgress)
        }
        onProgress(100)
    }

    /**
     * Rebuilds the original archive from a mounted workspace and atomically replaces it.
     * This is what makes rename/delete/edit/copy operations inside an opened archive persistent.
     */
    fun replaceFromDirectory(
        archive: File,
        workspace: File,
        password: String = "",
        level: ArchiveLevel = ArchiveLevel.NORMAL,
    ) {
        val format = ArchiveFormat.fromFile(archive)
            ?: throw IOException("Unsupported archive format: ${archive.name}")
        val sources = workspace.listFiles()?.sortedBy { it.name.lowercase() }?.toList().orEmpty()
        if ((format == ArchiveFormat.GZIP || format == ArchiveFormat.XZ) &&
            (sources.size != 1 || !sources.single().isFile)
        ) {
            throw IOException("${format.label} archives must contain exactly one regular file")
        }

        val parent = archive.parentFile ?: throw IOException("Archive has no parent directory")
        val temp = File(parent, ".${archive.name}.${System.nanoTime()}.mtapk.tmp")
        try {
            createAt(temp, sources, format, level, password)
            if (!temp.isFile || temp.length() == 0L) throw IOException("Temporary archive was not created")
            runCatching {
                Files.move(
                    temp.toPath(),
                    archive.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE,
                )
            }.recoverCatching {
                Files.move(temp.toPath(), archive.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }.getOrThrow()
        } finally {
            if (temp.exists()) temp.delete()
        }
    }

    private fun createSingle(sources: List<File>, request: ArchiveRequest): List<File> {
        if ((request.format == ArchiveFormat.GZIP || request.format == ArchiveFormat.XZ) &&
            (sources.size != 1 || !sources.single().isFile)
        ) {
            throw IOException("${request.format.label} can compress exactly one regular file. Use tar.${if (request.format == ArchiveFormat.GZIP) "gz" else "xz"} for folders or multiple files.")
        }

        val output = uniqueFile(request.outputDirectory, normalizedFileName(request.fileName, request.format))
        createAt(output, sources, request.format, request.level, request.password)
        if (!output.isFile || output.length() == 0L) throw IOException("Archive was not created: $output")
        return if (request.splitLengthBytes > 0L && output.length() > request.splitLengthBytes) {
            splitFile(output, request.splitLengthBytes)
        } else listOf(output)
    }

    private fun createAt(output: File, sources: List<File>, format: ArchiveFormat, level: ArchiveLevel, password: String) {
        if (output.exists() && !output.delete()) throw IOException("Cannot replace temporary archive: $output")
        when (format) {
            ArchiveFormat.ZIP -> createZip(output, sources, level, password)
            ArchiveFormat.SEVEN_Z -> create7z(output, sources, level, password)
            ArchiveFormat.TAR -> createTar(output, sources) { it }
            ArchiveFormat.TAR_GZ -> createTar(output, sources) { gzipStream(it, level) }
            ArchiveFormat.TAR_XZ -> createTar(output, sources) { XZCompressorOutputStream(it, level.preset()) }
            ArchiveFormat.TAR_ZST -> createTar(output, sources) { ZstdCompressorOutputStream(it, level.preset()) }
            ArchiveFormat.TAR_BZ2 -> createTar(output, sources) { BZip2CompressorOutputStream(it, level.bzipBlockSize()) }
            ArchiveFormat.TAR_LZ4 -> createTar(output, sources) { FramedLZ4CompressorOutputStream(it) }
            ArchiveFormat.GZIP -> compressSingle(output, sources.single()) { gzipStream(it, level) }
            ArchiveFormat.XZ -> compressSingle(output, sources.single()) { XZCompressorOutputStream(it, level.preset()) }
        }
    }

    private fun createZip(output: File, sources: List<File>, level: ArchiveLevel, password: String) {
        // zip4j does not need to be involved when an edited archive becomes empty.
        // A standards-compliant empty ZIP still lets the explorer delete the final entry
        // and persist that change back to the original archive.
        if (sources.isEmpty()) {
            java.util.zip.ZipOutputStream(BufferedOutputStream(FileOutputStream(output))).use { }
            return
        }
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
        try {
            if (file.isFile) {
                BufferedInputStream(FileInputStream(file)).use { input ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        if (read > 0) seven.write(buffer, 0, read)
                    }
                }
            }
        } finally {
            seven.closeArchiveEntry()
        }
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

    private fun extractZip(archive: File, destination: File, password: String, onProgress: (Int) -> Unit) {
        val zip = if (password.isBlank()) ZipFile(archive) else ZipFile(archive, password.toCharArray())
        if (zip.isEncrypted && password.isBlank()) throw IOException("Password required for ${archive.name}")
        val headers = zip.fileHeaders.orEmpty()
        if (headers.isEmpty()) {
            onProgress(100)
            return
        }
        headers.forEachIndexed { index, header ->
            zip.extractFile(header, destination.absolutePath)
            onProgress(((index + 1) * 100 / headers.size).coerceIn(0, 100))
        }
    }

    private fun extract7z(archive: File, destination: File, password: String, onProgress: (Int) -> Unit) {
        fun openSeven() = SevenZFile.builder().setFile(archive).let { builder ->
            if (password.isNotBlank()) builder.setPassword(password.toCharArray())
            builder.get()
        }
        val totalEntries = openSeven().use { seven ->
            var count = 0
            while (seven.nextEntry != null) count++
            count.coerceAtLeast(1)
        }
        openSeven().use { seven ->
            val buffer = ByteArray(64 * 1024)
            var processed = 0
            while (true) {
                val entry = seven.nextEntry ?: break
                val output = safeDestination(destination, entry.name)
                if (entry.isDirectory) {
                    if (!output.exists() && !output.mkdirs()) throw IOException("Cannot create directory: $output")
                } else {
                    output.parentFile?.let { parent ->
                        if (!parent.exists() && !parent.mkdirs()) throw IOException("Cannot create directory: $parent")
                    }
                    BufferedOutputStream(FileOutputStream(output)).use { out ->
                        while (true) {
                            val read = seven.read(buffer)
                            if (read < 0) break
                            if (read > 0) out.write(buffer, 0, read)
                        }
                    }
                    if (entry.hasLastModifiedDate) output.setLastModified(entry.lastModifiedDate.time)
                }
                processed++
                onProgress((processed * 100 / totalEntries).coerceIn(0, 100))
            }
        }
    }

    private fun extractTar(archive: File, destination: File, format: ArchiveFormat, onProgress: (Int) -> Unit) {
        ProgressInputStream(FileInputStream(archive), archive.length(), onProgress).use { progressRaw ->
            BufferedInputStream(progressRaw).use { raw ->
                val wrapped: InputStream = when (format) {
                    ArchiveFormat.TAR -> raw
                    ArchiveFormat.TAR_GZ -> GzipCompressorInputStream(raw)
                    ArchiveFormat.TAR_XZ -> XZCompressorInputStream(raw)
                    ArchiveFormat.TAR_ZST -> ZstdCompressorInputStream(raw)
                    ArchiveFormat.TAR_BZ2 -> BZip2CompressorInputStream(raw)
                    ArchiveFormat.TAR_LZ4 -> FramedLZ4CompressorInputStream(raw)
                    else -> throw IOException("Not a tar archive: ${archive.name}")
                }
                wrapped.use { input ->
                    TarArchiveInputStream(input).use { tar ->
                        while (true) {
                            val entry = tar.nextTarEntry ?: break
                            if (entry.isSymbolicLink || entry.isLink) continue
                            val output = safeDestination(destination, entry.name)
                            if (entry.isDirectory) {
                                if (!output.exists() && !output.mkdirs()) throw IOException("Cannot create directory: $output")
                            } else {
                                output.parentFile?.let { parent ->
                                    if (!parent.exists() && !parent.mkdirs()) throw IOException("Cannot create directory: $parent")
                                }
                                BufferedOutputStream(FileOutputStream(output)).use { out -> tar.copyTo(out) }
                                output.setLastModified(entry.lastModifiedDate.time)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun extractSingleCompressed(archive: File, destination: File, format: ArchiveFormat, onProgress: (Int) -> Unit) {
        val outputName = when (format) {
            ArchiveFormat.GZIP -> archive.name.removeSuffix(".gz").ifBlank { "content" }
            ArchiveFormat.XZ -> archive.name.removeSuffix(".xz").ifBlank { "content" }
            else -> throw IOException("Unsupported single-stream archive")
        }
        val output = safeDestination(destination, outputName)
        ProgressInputStream(FileInputStream(archive), archive.length(), onProgress).use { progressRaw ->
            BufferedInputStream(progressRaw).use { raw ->
                val input: InputStream = when (format) {
                    ArchiveFormat.GZIP -> GzipCompressorInputStream(raw)
                    ArchiveFormat.XZ -> XZCompressorInputStream(raw)
                    else -> raw
                }
                input.use { compressed ->
                    BufferedOutputStream(FileOutputStream(output)).use { out -> compressed.copyTo(out) }
                }
            }
        }
    }

    private class ProgressInputStream(
        input: InputStream,
        private val totalBytes: Long,
        private val onProgress: (Int) -> Unit,
    ) : FilterInputStream(input) {
        private var readBytes = 0L
        private var lastProgress = -1

        override fun read(): Int {
            val value = super.read()
            if (value >= 0) report(1)
            return value
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            val read = super.read(buffer, offset, length)
            if (read > 0) report(read.toLong())
            return read
        }

        private fun report(delta: Long) {
            readBytes += delta
            val progress = if (totalBytes <= 0L) 0 else ((readBytes * 100L) / totalBytes).toInt().coerceIn(0, 99)
            if (progress != lastProgress) {
                lastProgress = progress
                onProgress(progress)
            }
        }
    }

    private fun safeDestination(root: File, entryName: String): File {
        val normalizedName = entryName.replace('\\', '/').trimStart('/')
        val output = File(root, normalizedName).canonicalFile
        val canonicalRoot = root.canonicalFile
        if (output != canonicalRoot && !output.path.startsWith(canonicalRoot.path + File.separator)) {
            throw IOException("Unsafe archive entry: $entryName")
        }
        return output
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
