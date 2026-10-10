package io.github.lootdev78.mtapktool.archive

import io.github.lootdev78.mtapktool.feature.explorer.util.deleteTreeSafely

import android.system.Os

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
import org.apache.commons.compress.archivers.tar.TarConstants
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream
import org.apache.commons.compress.archivers.zip.ZipFile as CommonsZipFile
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
import java.util.Date
import java.util.zip.CRC32

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
                else if (!source.deleteTreeSafely() && source.exists()) throw IOException("Cannot delete source: $source")
            }
        }
        return outputs
    }

    /** Extracts a supported archive into [ArchiveExtractRequest.outputDirectory]. */
    fun extract(
        request: ArchiveExtractRequest,
        onProgress: (Int) -> Unit = {},
        conflictResolver: (ArchiveEntryConflict) -> ArchiveConflictAction = { ArchiveConflictAction.OVERWRITE },
    ): File {
        val archive = request.archive
        require(archive.isFile) { "Archive does not exist: ${archive.absolutePath}" }
        val destination = request.outputDirectory
        if (!destination.exists() && !destination.mkdirs()) {
            throw IOException("Cannot create extraction directory: ${destination.absolutePath}")
        }
        if (!destination.isDirectory) throw IOException("Extraction target is not a directory: ${destination.absolutePath}")

        extractToDirectory(archive, destination, request.password, onProgress, conflictResolver, request.charset)
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
        conflictResolver: (ArchiveEntryConflict) -> ArchiveConflictAction = { ArchiveConflictAction.OVERWRITE },
        charset: String = "",
        checkCancelled: () -> Unit = {},
    ) {
        val format = ArchiveFormat.fromFile(archive)
            ?: throw IOException("Unsupported archive format: ${archive.name}")
        if (destination.exists() && !destination.isDirectory) {
            throw IOException("Extraction target is not a directory: ${destination.absolutePath}")
        }
        if (!destination.exists() && !destination.mkdirs()) {
            throw IOException("Cannot create extraction target: ${destination.absolutePath}")
        }

        checkCancelled()
        onProgress(0)
        when (format) {
            ArchiveFormat.ZIP -> extractZip(archive, destination, password, onProgress, conflictResolver, charset, checkCancelled)
            ArchiveFormat.SEVEN_Z -> extract7z(archive, destination, password, onProgress, conflictResolver)
            ArchiveFormat.TAR,
            ArchiveFormat.TAR_GZ,
            ArchiveFormat.TAR_XZ,
            ArchiveFormat.TAR_ZST,
            ArchiveFormat.TAR_BZ2,
            ArchiveFormat.TAR_LZ4 -> extractTar(archive, destination, format, onProgress, conflictResolver, charset, checkCancelled)
            ArchiveFormat.GZIP -> extractSingleCompressed(archive, destination, ArchiveFormat.GZIP, onProgress, conflictResolver)
            ArchiveFormat.XZ -> extractSingleCompressed(archive, destination, ArchiveFormat.XZ, onProgress, conflictResolver)
        }
        checkCancelled()
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
        charset: String = "",
        beforePublish: () -> Unit = {},
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
            createAt(temp, sources, format, level, password, templateArchive = archive, charset = charset)
            if (!temp.isFile || temp.length() == 0L) throw IOException("Temporary archive was not created")
            inspect(temp, format, password, charset, { _, _ -> }, {})
            beforePublish()
            runCatching { Os.chmod(temp.absolutePath, Os.stat(archive.absolutePath).st_mode and 0xFFF) }
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

    private fun createAt(
        output: File,
        sources: List<File>,
        format: ArchiveFormat,
        level: ArchiveLevel,
        password: String,
        templateArchive: File? = null,
        charset: String = "",
    ) {
        if (ArchiveCharsets.supports(format)) checkFileNames(sources, charset)
        if (output.exists() && !output.delete()) throw IOException("Cannot replace temporary archive: $output")
        when (format) {
            ArchiveFormat.ZIP -> if (templateArchive != null && password.isBlank()) {
                createZipPreserving(output, sources, level, templateArchive, charset)
            } else createZip(output, sources, level, password, charset)
            ArchiveFormat.SEVEN_Z -> create7z(output, sources, level, password)
            ArchiveFormat.TAR -> createTar(output, sources, templateArchive?.let { readTarMetadata(it, format, charset) }, charset) { it }
            ArchiveFormat.TAR_GZ -> createTar(output, sources, templateArchive?.let { readTarMetadata(it, format, charset) }, charset) { gzipStream(it, level) }
            ArchiveFormat.TAR_XZ -> createTar(output, sources, templateArchive?.let { readTarMetadata(it, format, charset) }, charset) { xzOutputStream(it, level) }
            ArchiveFormat.TAR_ZST -> createTar(output, sources, templateArchive?.let { readTarMetadata(it, format, charset) }, charset) { zstdOutputStream(it, level) }
            ArchiveFormat.TAR_BZ2 -> createTar(output, sources, templateArchive?.let { readTarMetadata(it, format, charset) }, charset) { BZip2CompressorOutputStream(it, level.bzipBlockSize()) }
            ArchiveFormat.TAR_LZ4 -> createTar(output, sources, templateArchive?.let { readTarMetadata(it, format, charset) }, charset) { FramedLZ4CompressorOutputStream(it) }
            ArchiveFormat.GZIP -> compressSingle(output, sources.single()) { gzipStream(it, level) }
            ArchiveFormat.XZ -> compressSingle(output, sources.single()) { xzOutputStream(it, level) }
        }
    }

    private fun createZip(output: File, sources: List<File>, level: ArchiveLevel, password: String, charset: String = "") {
        // zip4j does not need to be involved when an edited archive becomes empty.
        // A standards-compliant empty ZIP still lets the explorer delete the final entry
        // and persist that change back to the original archive.
        if (sources.isEmpty()) {
            java.util.zip.ZipOutputStream(BufferedOutputStream(FileOutputStream(output))).use { }
            return
        }
        val zip = if (password.isBlank()) ZipFile(output) else ZipFile(output, password.toCharArray())
        ArchiveCharsets.charset(charset)?.let { zip.charset = it }
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

    private data class ZipMeta(
        val comment: String?,
        val extra: ByteArray?,
        val unixMode: Int,
        val method: Int,
        val modified: Long,
    )

    private fun createZipPreserving(output: File, sources: List<File>, level: ArchiveLevel, template: File, charset: String = "") {
        val metadata = linkedMapOf<String, ZipMeta>()
        runCatching {
            val builder = CommonsZipFile.builder().setFile(template)
            ArchiveCharsets.charset(charset)?.let { builder.setCharset(it) }
            builder.get().use { zip ->
                val entries = zip.entries
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    metadata[entry.name] = ZipMeta(
                        comment = entry.comment,
                        extra = entry.extra,
                        unixMode = entry.unixMode,
                        method = entry.method,
                        modified = entry.lastModifiedDate?.time ?: entry.time,
                    )
                }
            }
        }
        ZipArchiveOutputStream(output).use { zip ->
            zip.setEncoding(charset.ifBlank { "UTF-8" })
            zip.setUseLanguageEncodingFlag(charset.isBlank() || ArchiveCharsets.charset(charset) == Charsets.UTF_8)
            zip.setCreateUnicodeExtraFields(ZipArchiveOutputStream.UnicodeExtraFieldPolicy.ALWAYS)
            zip.setLevel(level.preset())
            sources.forEach { source -> addToPreservingZip(zip, source.parentFile ?: source, source, metadata, level) }
            zip.finish()
        }
    }

    private fun addToPreservingZip(
        zip: ZipArchiveOutputStream,
        base: File,
        file: File,
        metadata: Map<String, ZipMeta>,
        level: ArchiveLevel,
    ) {
        val symbolic = Files.isSymbolicLink(file.toPath())
        val name = file.relativeTo(base).path.replace(File.separatorChar, '/').let {
            if (file.isDirectory && !symbolic) it.trimEnd('/') + "/" else it
        }
        val meta = metadata[name] ?: metadata[name.trimEnd('/')]
        val linkBytes = if (symbolic) runCatching { Files.readSymbolicLink(file.toPath()).toString().toByteArray(Charsets.UTF_8) }.getOrDefault(ByteArray(0)) else null
        val entry = ZipArchiveEntry(name).apply {
            time = file.lastModified()
            meta?.comment?.let { comment = it }
            meta?.extra?.let { extra = it }
            unixMode = if (symbolic) 0xA1FF else runCatching { Os.stat(file.absolutePath).st_mode }.getOrDefault(meta?.unixMode ?: if (file.isDirectory) 0x41ED else 0x81A4)
            method = when {
                symbolic -> meta?.method ?: java.util.zip.ZipEntry.DEFLATED
                file.isDirectory -> java.util.zip.ZipEntry.STORED
                level == ArchiveLevel.STORE -> java.util.zip.ZipEntry.STORED
                level == ArchiveLevel.APK_MODE && file.extension.lowercase() in alreadyCompressedExtensions -> java.util.zip.ZipEntry.STORED
                meta?.method == java.util.zip.ZipEntry.STORED -> java.util.zip.ZipEntry.STORED
                else -> java.util.zip.ZipEntry.DEFLATED
            }
            if (method == java.util.zip.ZipEntry.STORED) {
                if (symbolic) {
                    val bytes = linkBytes ?: ByteArray(0)
                    size = bytes.size.toLong()
                    crc = CRC32().apply { update(bytes) }.value
                } else if (file.isFile) {
                    size = file.length()
                    val checksum = CRC32()
                    FileInputStream(file).buffered().use { input ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            if (read > 0) checksum.update(buffer, 0, read)
                        }
                    }
                    crc = checksum.value
                } else {
                    size = 0L
                    crc = 0L
                }
            }
        }
        zip.putArchiveEntry(entry)
        when {
            symbolic -> linkBytes?.let(zip::write)
            file.isFile -> BufferedInputStream(FileInputStream(file)).use { it.copyTo(zip) }
        }
        zip.closeArchiveEntry()
        if (!symbolic && file.isDirectory) {
            file.listFiles()?.sortedBy { it.name.lowercase() }?.forEach { addToPreservingZip(zip, base, it, metadata, level) }
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

    private fun createTar(
        output: File,
        sources: List<File>,
        metadata: Map<String, TarMeta>? = null,
        charset: String = "",
        wrapper: (OutputStream) -> OutputStream,
    ) {
        BufferedOutputStream(FileOutputStream(output)).use { fileOut ->
            wrapper(fileOut).use { compressed ->
                TarArchiveOutputStream(compressed, charset.ifBlank { "UTF-8" }).use { tar ->
                    tar.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX)
                    tar.setBigNumberMode(TarArchiveOutputStream.BIGNUMBER_POSIX)
                    sources.forEach { source -> addToTar(tar, source.parentFile ?: source, source, metadata) }
                    tar.finish()
                }
            }
        }
    }

    private data class TarMeta(
        val mode: Int,
        val userId: Long,
        val groupId: Long,
        val userName: String,
        val groupName: String,
        val modified: Long,
        val linkName: String?,
    )

    private fun addToTar(tar: TarArchiveOutputStream, base: File, file: File, metadata: Map<String, TarMeta>? = null) {
        val symbolic = Files.isSymbolicLink(file.toPath())
        val name = file.relativeTo(base).path.replace(File.separatorChar, '/').let {
            if (file.isDirectory && !symbolic) it.trimEnd('/') + "/" else it
        }
        val meta = metadata?.get(name) ?: metadata?.get(name.trimEnd('/'))
        val entry = if (symbolic) {
            TarArchiveEntry(name, TarConstants.LF_SYMLINK).apply {
                linkName = runCatching { Files.readSymbolicLink(file.toPath()).toString() }.getOrDefault(meta?.linkName.orEmpty())
            }
        } else TarArchiveEntry(file, name)
        meta?.let {
            entry.mode = runCatching { Os.stat(file.absolutePath).st_mode and 0xFFF }.getOrDefault(it.mode)
            entry.setUserId(it.userId)
            entry.setGroupId(it.groupId)
            entry.userName = it.userName
            entry.groupName = it.groupName
            entry.modTime = Date(file.lastModified())
        }
        tar.putArchiveEntry(entry)
        if (!symbolic && file.isFile) BufferedInputStream(FileInputStream(file)).use { input -> input.copyTo(tar) }
        tar.closeArchiveEntry()
        if (!symbolic && file.isDirectory) file.listFiles()?.sortedBy { it.name.lowercase() }?.forEach { addToTar(tar, base, it, metadata) }
    }

    private fun readTarMetadata(archive: File, format: ArchiveFormat, charset: String = ""): Map<String, TarMeta> {
        val result = linkedMapOf<String, TarMeta>()
        runCatching {
            BufferedInputStream(FileInputStream(archive)).use { raw ->
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
                    TarArchiveInputStream(input, charset.ifBlank { "UTF-8" }).use { tar ->
                        while (true) {
                            val entry = tar.nextEntry ?: break
                            result[entry.name] = TarMeta(
                                mode = entry.mode,
                                userId = entry.longUserId,
                                groupId = entry.longGroupId,
                                userName = entry.userName.orEmpty(),
                                groupName = entry.groupName.orEmpty(),
                                modified = entry.lastModifiedDate.time,
                                linkName = entry.linkName,
                            )
                        }
                    }
                }
            }
        }
        return result
    }

    private fun extractZip(
        archive: File,
        destination: File,
        password: String,
        onProgress: (Int) -> Unit,
        conflictResolver: (ArchiveEntryConflict) -> ArchiveConflictAction,
        charset: String = "",
        checkCancelled: () -> Unit = {},
    ) {
        val zip = if (password.isBlank()) ZipFile(archive) else ZipFile(archive, password.toCharArray())
        ArchiveCharsets.charset(charset)?.let { zip.charset = it }
        if (zip.isEncrypted && password.isBlank()) throw IOException("Password required")
        val headers = zip.fileHeaders.orEmpty()
        if (headers.isEmpty()) { onProgress(100); return }
        val directoryModes = mutableListOf<Pair<File, Int>>()
        val directoryTimes = mutableListOf<Pair<File, Long>>()
        headers.forEachIndexed { index, header ->
            checkCancelled()
            val output = resolveExtractionTarget(destination, header.fileName, header.isDirectory, conflictResolver)
            if (output != null) {
                if (header.isDirectory) {
                    if (!output.exists() && !output.mkdirs()) throw IOException("Cannot create directory: $output")
                    directoryTimes += output to header.lastModifiedTimeEpoch
                } else {
                    output.parentFile?.let { parent -> if (!parent.exists() && !parent.mkdirs()) throw IOException("Cannot create directory: $parent") }
                    zip.getInputStream(header).use { input -> BufferedOutputStream(FileOutputStream(output)).use { out -> copyChecked(input, out, checkCancelled) } }
                    runCatching { output.setLastModified(header.lastModifiedTimeEpoch) }
                }
                val attributes = header.externalFileAttributes
                if (attributes != null && attributes.size >= 4) {
                    val mode = ((attributes[3].toInt() and 255) shl 8) or (attributes[2].toInt() and 255)
                    if (mode != 0) {
                        if (header.isDirectory) directoryModes += output to (mode and 0xFFF)
                        else runCatching { Os.chmod(output.absolutePath, mode and 0xFFF) }
                    }
                }
            }
            onProgress(((index + 1) * 100 / headers.size).coerceIn(0, 100))
        }
        directoryModes.asReversed().forEach { (directory, mode) -> runCatching { Os.chmod(directory.absolutePath, mode) } }
        directoryTimes.asReversed().forEach { (directory, time) -> directory.setLastModified(time) }
    }

    private fun extract7z(
        archive: File,
        destination: File,
        password: String,
        onProgress: (Int) -> Unit,
        conflictResolver: (ArchiveEntryConflict) -> ArchiveConflictAction,
    ) {
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
                val output = resolveExtractionTarget(destination, entry.name, entry.isDirectory, conflictResolver)
                if (output == null) {
                    processed++
                    onProgress((processed * 100 / totalEntries).coerceIn(0, 100))
                    continue
                }
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

    private fun extractTar(
        archive: File,
        destination: File,
        format: ArchiveFormat,
        onProgress: (Int) -> Unit,
        conflictResolver: (ArchiveEntryConflict) -> ArchiveConflictAction,
        charset: String = "",
        checkCancelled: () -> Unit = {},
    ) {
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
                    TarArchiveInputStream(input, charset.ifBlank { "UTF-8" }).use { tar ->
                        while (true) {
                            checkCancelled()
                            val entry = tar.nextEntry ?: break
                            val output = resolveExtractionTarget(destination, entry.name, entry.isDirectory, conflictResolver) ?: continue
                            if (entry.isSymbolicLink) {
                                output.parentFile?.mkdirs()
                                runCatching {
                                    Files.deleteIfExists(output.toPath())
                                    Files.createSymbolicLink(output.toPath(), java.nio.file.Paths.get(entry.linkName.orEmpty()))
                                }
                                continue
                            }
                            if (entry.isLink) {
                                output.parentFile?.mkdirs()
                                val target = safeDestination(destination, entry.linkName)
                                if (target.exists()) runCatching { Files.createLink(output.toPath(), target.toPath()) }
                                continue
                            }
                            if (entry.isDirectory) {
                                if (!output.exists() && !output.mkdirs()) throw IOException("Cannot create directory: $output")
                            } else {
                                output.parentFile?.let { parent ->
                                    if (!parent.exists() && !parent.mkdirs()) throw IOException("Cannot create directory: $parent")
                                }
                                BufferedOutputStream(FileOutputStream(output)).use { out -> copyChecked(tar, out, checkCancelled) }
                            }
                            output.setLastModified(entry.lastModifiedDate.time)
                            runCatching { Os.chmod(output.absolutePath, entry.mode and 0x0FFF) }
                        }
                    }
                }
            }
        }
    }

    private fun extractSingleCompressed(
        archive: File,
        destination: File,
        format: ArchiveFormat,
        onProgress: (Int) -> Unit,
        conflictResolver: (ArchiveEntryConflict) -> ArchiveConflictAction,
    ) {
        val outputName = when (format) {
            ArchiveFormat.GZIP -> archive.name.removeSuffix(".gz").ifBlank { "content" }
            ArchiveFormat.XZ -> archive.name.removeSuffix(".xz").ifBlank { "content" }
            else -> throw IOException("Unsupported single-stream archive")
        }
        val output = resolveExtractionTarget(destination, outputName, false, conflictResolver) ?: return
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

    private fun resolveExtractionTarget(
        destination: File,
        entryName: String,
        directory: Boolean,
        conflictResolver: (ArchiveEntryConflict) -> ArchiveConflictAction,
    ): File? {
        var output = safeDestination(destination, entryName)
        val symbolic = Files.isSymbolicLink(output.toPath())
        if (!output.exists() && !symbolic) return output
        if (directory && output.isDirectory && !symbolic) return output
        when (conflictResolver(ArchiveEntryConflict(entryName, output, directory))) {
            ArchiveConflictAction.OVERWRITE -> {
                if (symbolic) Files.deleteIfExists(output.toPath())
                else if (output.isDirectory) {
                    if (!output.deleteTreeSafely() && output.exists()) throw IOException("Cannot replace: $output")
                } else if (!output.delete() && output.exists()) throw IOException("Cannot replace: $output")
            }
            ArchiveConflictAction.SKIP -> return null
            ArchiveConflictAction.KEEP_BOTH -> output = uniqueExtractionTarget(output)
            ArchiveConflictAction.CANCEL -> throw ArchiveExtractionCancelledException()
        }
        return output
    }

    private fun uniqueExtractionTarget(file: File): File {
        val parent = file.parentFile ?: return file
        val name = file.name
        val dot = name.lastIndexOf('.').takeIf { it > 0 }
        val base = if (dot != null) name.substring(0, dot) else name
        val extension = if (dot != null) name.substring(dot) else ""
        var index = 1
        var candidate: File
        do { candidate = File(parent, "$base ($index)$extension"); index++ } while (candidate.exists())
        return candidate
    }

    private class ArchiveExtractionCancelledException : IOException("Extraction cancelled")

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

    fun test(
        archive: File,
        password: String = "",
        charset: String = "",
        onProgress: (Int, String) -> Unit = { _, _ -> },
        checkCancelled: () -> Unit = {},
    ): ArchiveTestResult = inspect(
        archive, ArchiveFormat.fromFile(archive) ?: throw IOException("Nicht unterstütztes Archiv"),
        password, charset, onProgress, checkCancelled,
    )

    /** Verifies every payload, including compression trailers, without creating output files. */
    private fun inspect(
        file: File,
        format: ArchiveFormat,
        password: String,
        charset: String,
        onProgress: (Int, String) -> Unit,
        checkCancelled: () -> Unit,
    ): ArchiveTestResult {
        require(file.isFile) { "Archiv nicht gefunden" }
        var entries = 0
        var bytes = 0L
        checkCancelled()
        onProgress(0, "Archiv wird geprüft")
        when (format) {
            ArchiveFormat.ZIP -> {
                val zip = if (password.isBlank()) ZipFile(file) else ZipFile(file, password.toCharArray())
                zip.use {
                    ArchiveCharsets.charset(charset)?.let { zip.charset = it }
                    if (!zip.isValidZipFile) throw IOException("Ungültige ZIP-Struktur")
                    if (zip.isEncrypted && password.isBlank()) throw IOException("Passwort erforderlich")
                    val headers = zip.fileHeaders.orEmpty()
                    headers.forEachIndexed { index, header ->
                        checkCancelled()
                        onProgress(index * 100 / headers.size.coerceAtLeast(1), header.fileName)
                        if (!header.isDirectory) {
                            try {
                                zip.getInputStream(header).use { input ->
                                    // AES uses Zip4j's authentication checks; AE-2 does not advertise a CRC.
                                    val crc = if (header.encryptionMethod == EncryptionMethod.AES) -1L else header.crc
                                    bytes += ArchiveReadVerifier.read(input, header.uncompressedSize, crc) { checkCancelled() }
                                }
                            } catch (error: IOException) { throw IOException(header.fileName + ": " + error.message, error) }
                        }
                        entries++
                    }
                }
            }
            ArchiveFormat.SEVEN_Z -> {
                val builder = SevenZFile.builder().setFile(file)
                if (password.isNotBlank()) builder.setPassword(password.toCharArray())
                builder.get().use { seven ->
                    while (true) {
                        checkCancelled()
                        val entry = seven.nextEntry ?: break
                        onProgress((entries * 2).coerceAtMost(99), entry.name)
                        if (!entry.isDirectory) {
                            val stream = object : InputStream() {
                                override fun read(): Int = seven.read()
                                override fun read(buffer: ByteArray, offset: Int, length: Int): Int = seven.read(buffer, offset, length)
                            }
                            bytes += ArchiveReadVerifier.read(stream, entry.size, -1L) { checkCancelled() }
                        }
                        entries++
                    }
                }
            }
            else -> {
                var entryName = file.name
                ProgressInputStream(FileInputStream(file), file.length()) { progress -> onProgress(progress, entryName) }.use { raw ->
                    BufferedInputStream(raw).use { buffered ->
                        compressedInput(buffered, format).use { decoded ->
                            if (format == ArchiveFormat.GZIP || format == ArchiveFormat.XZ) {
                                bytes = ArchiveReadVerifier.read(decoded, -1L, -1L) { checkCancelled() }
                                entries = 1
                            } else {
                                var decodedBytes = 0L
                                val counted = object : FilterInputStream(decoded) {
                                    override fun read(): Int = decoded.read().also { if (it >= 0) decodedBytes++ }
                                    override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
                                        decoded.read(buffer, offset, length).also { if (it > 0) decodedBytes += it }
                                }
                                TarArchiveInputStream(counted, charset.ifBlank { "UTF-8" }).use { tar ->
                                    while (true) {
                                        checkCancelled()
                                        val entry = tar.nextEntry ?: break
                                        entryName = entry.name
                                        if (!entry.isCheckSumOK) throw IOException(entry.name + ": TAR-Kopfprüfsumme stimmt nicht")
                                        if (!tar.canReadEntryData(entry)) throw IOException(entry.name + ": Nicht unterstützter TAR-Eintrag")
                                        val size = if (entry.isSparse) entry.realSize else entry.size
                                        bytes += ArchiveReadVerifier.read(tar, size, -1L) { checkCancelled() }
                                        entries++
                                    }
                                    // TAR's end markers may precede a compressed stream's CRC/authentication trailer.
                                    if (decodedBytes < 512L) throw IOException("Fehlende oder abgeschnittene TAR-Kopfzeile")
                                    ArchiveReadVerifier.read(counted, -1L, -1L) { checkCancelled() }
                                }
                            }
                        }
                    }
                }
            }
        }
        checkCancelled()
        onProgress(100, "Alle Einträge vollständig geprüft")
        val checks = when (format) {
            ArchiveFormat.ZIP -> "Einträge, Größen und CRC-Prüfsummen geprüft; verschlüsselte AES-Einträge über ihre Authentifizierung."
            ArchiveFormat.SEVEN_Z -> "Alle Einträge vollständig gelesen; 7z-Prüfsummen und Größen geprüft."
            ArchiveFormat.TAR -> "TAR-Kopfprüfsummen, Größen und Lesbarkeit geprüft. TAR enthält keine Prüfsummen für den Dateiinhalt."
            ArchiveFormat.TAR_GZ, ArchiveFormat.TAR_XZ, ArchiveFormat.TAR_ZST, ArchiveFormat.TAR_BZ2, ArchiveFormat.TAR_LZ4 ->
                "TAR-Kopfprüfsummen und Größen geprüft; Kompressionsdaten bis zum Ende gelesen und vorhandene Format-Prüfsummen geprüft."
            ArchiveFormat.GZIP, ArchiveFormat.XZ -> "Kompressionsdaten bis zum Ende gelesen und vorhandene Format-Prüfsummen geprüft."
        }
        return ArchiveTestResult(entries, bytes, checks)
    }

    private fun checkFileNames(sources: List<File>, charset: String) {
        val encoding = ArchiveCharsets.charset(charset) ?: return
        val encoder = encoding.newEncoder()
        fun checkName(name: String) {
            if (!encoder.canEncode(name) || String(name.toByteArray(encoding), encoding) != name) {
                throw IOException("Dateiname kann mit " + charset + " nicht unverändert gespeichert werden: " + name + ". UTF-8 wählen.")
            }
        }
        sources.forEach { source ->
            checkName(source.name)
            source.walkTopDown().onEnter { !Files.isSymbolicLink(it.toPath()) }.forEach { file ->
                checkName(file.relativeTo(source.parentFile ?: source).invariantSeparatorsPath)
            }
        }
    }

    private fun copyChecked(input: InputStream, output: OutputStream, checkCancelled: () -> Unit) {
        val buffer = ByteArray(65536)
        while (true) {
            checkCancelled()
            val read = input.read(buffer)
            if (read < 0) break
            if (read > 0) output.write(buffer, 0, read)
        }
    }

    private fun compressedInput(raw: InputStream, format: ArchiveFormat): InputStream = when (format) {
        ArchiveFormat.TAR -> raw
        ArchiveFormat.TAR_GZ, ArchiveFormat.GZIP -> GzipCompressorInputStream(raw, true)
        ArchiveFormat.TAR_XZ, ArchiveFormat.XZ -> XZCompressorInputStream(raw, true)
        ArchiveFormat.TAR_ZST -> ZstdCompressorInputStream(raw)
        ArchiveFormat.TAR_BZ2 -> BZip2CompressorInputStream(raw, true)
        ArchiveFormat.TAR_LZ4 -> FramedLZ4CompressorInputStream(raw, true)
        else -> throw IOException("Nicht unterstützter Kompressionsstrom")
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

    private fun xzOutputStream(out: OutputStream, level: ArchiveLevel): XZCompressorOutputStream {
        return XZCompressorOutputStream(out, level.preset())
    }

    private fun zstdOutputStream(out: OutputStream, level: ArchiveLevel): ZstdCompressorOutputStream {
        return ZstdCompressorOutputStream(out, level.preset())
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
