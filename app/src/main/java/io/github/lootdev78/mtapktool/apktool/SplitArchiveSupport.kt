package io.github.lootdev78.mtapktool.apktool

import io.github.apktool.android.runtime.ApktoolCommandRunner
import io.github.apktool.android.runtime.ShellTokenizer
import io.github.apktool.android.runtime.Toolchain
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.Locale
import java.util.concurrent.CancellationException
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

/**
 * Split-APK archive handling kept in the MTExplorer integration layer.
 *
 * The base/split classification follows the practical rules used by AntiSplit-M
 * (Apache-2.0): base.apk is preferred and config-/split-prefixed entries are treated as
 * configuration splits. MTApktool adds bundletool/APKM/XAPK naming heuristics and
 * a universal.apk preference for decode-only workflows.
 */
object SplitArchiveSupport {
    private val supportedExtensions = setOf("apks", "apkm", "xapk")

    data class ApkEntry(
        val path: String,
        val size: Long,
        val preferred: Boolean = false,
    ) {
        val displayName: String get() = File(path).name
        val sizeMiB: Double get() = if (size > 0) size / (1024.0 * 1024.0) else 0.0
    }

    fun isSplitArchive(file: File): Boolean =
        file.isFile && file.extension.lowercase(Locale.ROOT) in supportedExtensions

    fun inspect(file: File): List<ApkEntry> {
        if (!isSplitArchive(file)) return emptyList()
        ZipFile(file).use { zip ->
            val raw = zip.entries().asSequence()
                .filter { !it.isDirectory && it.name.lowercase(Locale.ROOT).endsWith(".apk") }
                .map { ApkEntry(path = it.name, size = it.size) }
                .toList()
            if (raw.isEmpty()) return emptyList()
            val preferred = choosePreferred(raw)
            return raw.map { it.copy(preferred = it.path == preferred.path) }
                .sortedWith(compareByDescending<ApkEntry> { it.preferred }.thenBy { it.path.lowercase(Locale.ROOT) })
        }
    }

    fun isSplitDecodeCommand(command: String): Boolean {
        val args = runCatching { ShellTokenizer.split(command.trim()) }.getOrNull() ?: return false
        if (args.isEmpty()) return false
        var i = 0
        if (args[0].equals("apktool", ignoreCase = true)) i++
        return args.getOrNull(i)?.let {
            it.equals("apks-decode", ignoreCase = true) || it.equals("split-decode", ignoreCase = true)
        } == true
    }

    @Throws(Exception::class)
    fun executeDecode(
        command: String,
        toolchain: Toolchain,
        listener: ApktoolCommandRunner.Listener,
    ): ApktoolCommandRunner.Result {
        val parsed = parse(command)
        val archive = File(parsed.archivePath)
        if (!archive.isFile) throw IOException("Split archive not found: $archive")
        if (!isSplitArchive(archive)) throw IOException("Unsupported split archive type: ${archive.extension}")

        val outputRoot = File(parsed.outputPath)
        if (parsed.allSplits) {
            if (!outputRoot.isDirectory && !outputRoot.mkdirs()) {
                throw IOException("Cannot create output directory: $outputRoot")
            }
        } else {
            outputRoot.parentFile?.let { parent ->
                if (!parent.isDirectory && !parent.mkdirs()) throw IOException("Cannot create output parent: $parent")
            }
        }

        val tempRoot = File(toolchain.inputDir, "split-${System.currentTimeMillis()}-${archive.name.hashCode().toUInt()}")
        if (!tempRoot.mkdirs() && !tempRoot.isDirectory) throw IOException("Cannot create temporary directory: $tempRoot")

        try {
            ZipFile(archive).use { zip ->
                val entries = zip.entries().asSequence()
                    .filter { !it.isDirectory && it.name.lowercase(Locale.ROOT).endsWith(".apk") }
                    .toList()
                if (entries.isEmpty()) throw IOException("No APK entries found in ${archive.name}")

                val selected = when {
                    parsed.allSplits -> entries
                    parsed.entryPath != null -> listOf(entries.firstOrNull { it.name == parsed.entryPath }
                        ?: throw IOException("Selected APK entry not found: ${parsed.entryPath}"))
                    else -> listOf(choosePreferredZip(entries))
                }

                listener.onLine("I: ${archive.extension.uppercase(Locale.ROOT)} contains ${entries.size} APK(s); ${selected.size} selected")
                val runner = ApktoolCommandRunner(toolchain, listener)
                var lastOutput: File? = null
                val force = parsed.decodeFlags.any { it == "-f" || it == "--force" }

                selected.forEachIndexed { index, entry ->
                    checkCancelled()
                    val safeName = sanitize(File(entry.name).name.ifBlank { "split-$index.apk" })
                    val extracted = Toolchain.uniqueFile(tempRoot, safeName)
                    zip.getInputStream(entry).use { input ->
                        FileOutputStream(extracted).use { output -> copyCancellable(input, output) }
                    }

                    val base = safeName.replace(Regex("(?i)\\.apk$"), "").ifBlank { "split-$index" }
                    val target = if (parsed.allSplits) {
                        val desired = File(outputRoot, base)
                        if (force || !desired.exists()) desired else uniqueDirectory(outputRoot, base)
                    } else outputRoot

                    val decodeArgs = buildList {
                        add("apktool")
                        add("decode")
                        addAll(parsed.decodeFlags)
                        add("-o")
                        add(target.absolutePath)
                        add(extracted.absolutePath)
                    }
                    listener.onLine("I: Decode split ${index + 1}/${selected.size}: ${entry.name}")
                    val result = runner.execute(decodeArgs.joinToString(" ") { ShellTokenizer.quote(it) })
                    if (!result.isSuccess) return result
                    lastOutput = result.output ?: target
                }

                return ApktoolCommandRunner.Result(
                    0,
                    if (parsed.allSplits) "Split archive decode complete (${selected.size} projects)" else "Split archive base decode complete",
                    lastOutput ?: outputRoot,
                )
            }
        } finally {
            deleteRecursively(tempRoot)
        }
    }

    private data class Parsed(
        val archivePath: String,
        val outputPath: String,
        val allSplits: Boolean,
        val entryPath: String?,
        val decodeFlags: List<String>,
    )

    private fun parse(command: String): Parsed {
        val args = ShellTokenizer.split(command.trim()).toMutableList()
        if (args.firstOrNull()?.equals("apktool", ignoreCase = true) == true) args.removeAt(0)
        val verb = args.removeFirstOrNull() ?: throw IllegalArgumentException("Missing split decode command")
        if (!verb.equals("apks-decode", true) && !verb.equals("split-decode", true)) {
            throw IllegalArgumentException("Unsupported split decode command: $verb")
        }

        val delimiter = args.indexOf("--")
        val head = if (delimiter >= 0) args.subList(0, delimiter).toList() else args.toList()
        val decodeFlags = if (delimiter >= 0) args.subList(delimiter + 1, args.size).toList() else emptyList()

        var allSplits = false
        var entry: String? = null
        val positional = mutableListOf<String>()
        var i = 0
        while (i < head.size) {
            when (val token = head[i]) {
                "--all-splits" -> allSplits = true
                "--entry" -> {
                    entry = head.getOrNull(i + 1) ?: throw IllegalArgumentException("--entry requires a ZIP entry path")
                    i++
                }
                else -> positional += token
            }
            i++
        }
        if (positional.size != 2) {
            throw IllegalArgumentException("apks-decode [--all-splits | --entry name.apk] <archive> <output-dir> -- [decode options]")
        }
        if (allSplits && entry != null) throw IllegalArgumentException("--all-splits and --entry cannot be combined")
        return Parsed(positional[0], positional[1], allSplits, entry, decodeFlags)
    }

    private fun choosePreferred(entries: List<ApkEntry>): ApkEntry = entries.maxBy(::score)

    private fun choosePreferredZip(entries: List<ZipEntry>): ZipEntry = entries.maxBy { entry ->
        score(ApkEntry(entry.name, entry.size))
    }

    private fun score(entry: ApkEntry): Long {
        val path = entry.path.lowercase(Locale.ROOT)
        val name = File(path).name
        var score = 0L
        when {
            name == "universal.apk" -> score += 1_000_000
            name == "base.apk" -> score += 950_000
            name == "base-master.apk" -> score += 925_000
            name.startsWith("base-") && !name.contains("config") -> score += 850_000
            isAntiSplitBaseName(name) -> score += 700_000
        }
        if (path.contains("standalones/")) score += 500_000
        if (!path.contains('/')) score += 200_000
        if (name.startsWith("config") || name.startsWith("split") || name.contains("split_config")) score -= 500_000
        if (entry.size > 0) score += minOf(100_000L, entry.size / 1024L)
        return score
    }

    /** Mirrors AntiSplit-M's base APK rule while keeping exact names above higher priority. */
    private fun isAntiSplitBaseName(name: String): Boolean =
        name == "base.apk" || (!name.startsWith("config") && !name.startsWith("split"))

    private fun uniqueDirectory(parent: File, base: String): File {
        var i = 1
        while (true) {
            val candidate = File(parent, "$base-$i")
            if (!candidate.exists()) return candidate
            i++
        }
    }

    private fun sanitize(name: String): String = name.replace(Regex("[^A-Za-z0-9._() +@-]"), "_").ifBlank { "split.apk" }

    private fun copyCancellable(input: java.io.InputStream, output: java.io.OutputStream) {
        val buffer = ByteArray(1024 * 1024)
        while (true) {
            checkCancelled()
            val count = input.read(buffer)
            if (count < 0) return
            if (count > 0) output.write(buffer, 0, count)
        }
    }

    private fun deleteRecursively(file: File) {
        if (!file.exists()) return
        if (file.isDirectory) file.listFiles()?.forEach(::deleteRecursively)
        file.delete()
    }

    private fun checkCancelled() {
        if (Thread.currentThread().isInterrupted) throw CancellationException("Job cancelled")
    }
}
