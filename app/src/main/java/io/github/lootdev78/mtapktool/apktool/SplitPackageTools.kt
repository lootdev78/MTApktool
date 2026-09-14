package io.github.lootdev78.mtapktool.apktool

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.util.DisplayMetrics
import io.github.muntashirakon.zipalign.ZipAlign
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * APKS/APKM/XAPK/APKX operations used by the explorer.
 *
 * Installation keeps the original split APKs and writes the selected entries into one
 * PackageInstaller session. Conversion prefers an existing universal APK. If a universal APK
 * is unavailable, a conservative AntiSplit compatibility merge is performed from the base APK.
 */
object SplitPackageTools {
    data class ConvertOptions(
        val selectedPaths: Set<String>? = null,
        val includeOptionalSplits: Boolean = true,
        val includeFeatureSplits: Boolean = true,
        val keepExtractedSplits: Boolean = false,
        val cleanMetaInf: Boolean = true,
        val compressionLevel: Int = 6,
        val zipAlign: Boolean = true,
        val alignment: Int = 4,
        val sharedLibraryAlignment: Int = 16384,
        val force: Boolean = true,
        val verifyAlignment: Boolean = true,
    )

    fun entries(container: File): List<SplitArchiveSupport.ApkEntry> = SplitArchiveSupport.inspect(container)

    /** Best-effort device matching for standard bundletool/APKM/XAPK split names. */
    fun selectForDevice(context: Context, source: List<SplitArchiveSupport.ApkEntry>, includeFeatures: Boolean = true): Set<String> {
        if (source.isEmpty()) return emptySet()
        source.firstOrNull { File(it.path).name.equals("universal.apk", true) }?.let { return setOf(it.path) }

        val preferred = source.firstOrNull { it.preferred } ?: source.first()
        val abis = Build.SUPPORTED_ABIS.map { normalizeToken(it) }.toSet()
        val density = densityQualifier(context.resources.displayMetrics)
        val languages = buildSet {
            val locales = context.resources.configuration.locales
            for (i in 0 until locales.size()) {
                val locale = locales[i]
                add(locale.language.lowercase(Locale.ROOT))
                if (locale.country.isNotBlank()) add((locale.language + "-r" + locale.country).lowercase(Locale.ROOT))
                if (locale.script.isNotBlank()) add((locale.language + "-" + locale.script).lowercase(Locale.ROOT))
            }
            add(Locale.getDefault().language.lowercase(Locale.ROOT))
        }

        val selected = linkedSetOf(preferred.path)
        source.forEach { item ->
            if (item.path == preferred.path) return@forEach
            val name = File(item.path).nameWithoutExtension.lowercase(Locale.ROOT)
            val normalized = normalizeToken(name)

            val abiTokens = setOf("armeabi", "armeabi_v7a", "arm64_v8a", "x86", "x86_64", "mips", "mips64", "riscv64")
            val abiInName = abiTokens.firstOrNull { token -> normalized.contains(token) }
            if (abiInName != null) {
                if (abiInName in abis) selected += item.path
                return@forEach
            }

            val densityTokens = setOf("ldpi", "mdpi", "tvdpi", "hdpi", "xhdpi", "xxhdpi", "xxxhdpi", "anydpi", "nodpi")
            val densityInName = densityTokens.firstOrNull { name.contains(it) }
            if (densityInName != null) {
                if (densityInName == density || densityInName == "anydpi" || densityInName == "nodpi") selected += item.path
                return@forEach
            }

            val localeToken = languageQualifierFromName(name)
            if (localeToken != null) {
                val language = localeToken.substringBefore('-').substringBefore("_r")
                if (localeToken in languages || language in languages) selected += item.path
                return@forEach
            }

            if (isConfigurationSplit(name)) {
                // Unknown configuration types are safer to leave out in automatic mode.
                return@forEach
            }

            if (includeFeatures) selected += item.path
        }
        return selected
    }

    fun extractPreferred(container: File, outputDirectory: File = container.parentFile ?: File(".")): File {
        val list = entries(container)
        val preferred = list.firstOrNull { it.preferred } ?: list.firstOrNull()
            ?: throw IOException("No APK found in ${container.name}")
        return extractEntry(container, preferred.path, uniqueFile(outputDirectory, preferred.displayName))
    }

    fun extractSelected(
        container: File,
        selectedPaths: Set<String>,
        outputDirectory: File = uniqueDirectory(container.parentFile ?: File("."), container.nameWithoutExtension + "-apks"),
    ): List<File> {
        if (selectedPaths.isEmpty()) return emptyList()
        if (!outputDirectory.isDirectory && !outputDirectory.mkdirs()) throw IOException("Cannot create $outputDirectory")
        val result = mutableListOf<File>()
        ZipFile(container).use { zip ->
            selectedPaths.forEachIndexed { index, path ->
                val entry = zip.getEntry(path) ?: return@forEachIndexed
                val out = uniqueFile(outputDirectory, File(entry.name).name.ifBlank { "split-$index.apk" })
                zip.getInputStream(entry).use { input -> out.outputStream().buffered().use { output -> input.copyTo(output) } }
                result += out
            }
        }
        return result
    }

    fun extractAll(container: File, outputDirectory: File = uniqueDirectory(container.parentFile ?: File("."), container.nameWithoutExtension + "-apks")): List<File> {
        val all = entries(container).mapTo(linkedSetOf()) { it.path }
        return extractSelected(container, all, outputDirectory)
    }

    fun convertToApk(container: File, output: File, options: ConvertOptions): File {
        val allEntries = entries(container)
        if (allEntries.isEmpty()) throw IOException("No APK found in ${container.name}")
        val initiallySelected = options.selectedPaths?.takeIf { it.isNotEmpty() }
            ?.let { paths -> allEntries.filter { it.path in paths } }
            .orEmpty()
            .ifEmpty { allEntries }
        val selected = if (options.includeFeatureSplits) initiallySelected else initiallySelected.filter { entry ->
            entry.preferred || File(entry.path).name.equals("universal.apk", true) || isConfigurationSplit(File(entry.path).name.lowercase(Locale.ROOT))
        }
        if (selected.none { it.preferred } && selected.none { File(it.path).name.equals("universal.apk", true) }) {
            throw IOException("Base/universal APK must be selected")
        }
        output.parentFile?.let { if (!it.isDirectory && !it.mkdirs()) throw IOException("Cannot create ${it.absolutePath}") }
        if (output.exists() && !options.force) throw IOException("Output already exists: $output")

        val universal = selected.firstOrNull { File(it.path).name.equals("universal.apk", true) }
        val work = File(output.parentFile ?: container.parentFile, ".${output.name}.${System.nanoTime()}.merge.tmp")
        try {
            if (universal != null) {
                extractEntry(container, universal.path, work)
            } else {
                mergeCompatibility(container, selected, work, options)
            }

            if (options.zipAlign) {
                val aligned = ZipAlign.doZipAlign(
                    work.absolutePath,
                    output.absolutePath,
                    validPowerOfTwo(options.alignment, 4),
                    if (options.sharedLibraryAlignment == 0) 0 else validPowerOfTwo(options.sharedLibraryAlignment, 16384),
                    true,
                )
                if (!aligned || !output.isFile) throw IOException("zipalign failed for ${output.name}")
                if (options.verifyAlignment && !ZipAlign.isZipAligned(
                        output.absolutePath,
                        validPowerOfTwo(options.alignment, 4),
                        if (options.sharedLibraryAlignment == 0) 0 else validPowerOfTwo(options.sharedLibraryAlignment, 16384),
                    )
                ) throw IOException("Alignment verification failed for ${output.name}")
            } else {
                if (output.exists() && !output.delete()) throw IOException("Cannot replace ${output.absolutePath}")
                if (!work.renameTo(output)) work.copyTo(output, overwrite = true)
            }

            if (options.keepExtractedSplits) {
                extractSelected(
                    container,
                    selected.mapTo(linkedSetOf()) { it.path },
                    uniqueDirectory(output.parentFile ?: container.parentFile, container.nameWithoutExtension + "-splits"),
                )
            }
            return output
        } finally {
            if (work.exists()) work.delete()
        }
    }

    /** Install selected splits in one PackageInstaller session. Android shows its normal confirmation UI. */
    fun install(context: Context, container: File, selectedPaths: Set<String>? = null): Int {
        val available = entries(container)
        val wanted = selectedPaths?.takeIf { it.isNotEmpty() } ?: available.mapTo(linkedSetOf()) { it.path }
        val chosen = available.filter { it.path in wanted }
        if (chosen.isEmpty()) throw IOException("No APKs selected in ${container.name}")
        if (chosen.none { it.preferred } && chosen.none { File(it.path).name.equals("universal.apk", true) }) {
            throw IOException("Base/universal APK must be selected for installation")
        }

        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        val total = chosen.sumOf { it.size.coerceAtLeast(0L) }
        if (total > 0L) params.setSize(total)
        val sessionId = installer.createSession(params)
        installer.openSession(sessionId).use { session ->
            ZipFile(container).use { zip ->
                chosen.forEachIndexed { index, item ->
                    val entry = zip.getEntry(item.path) ?: throw IOException("Missing APK entry: ${item.path}")
                    val safeName = File(entry.name).name.ifBlank { "split-$index.apk" }
                    session.openWrite(safeName, 0, entry.size.coerceAtLeast(-1L)).use { out ->
                        zip.getInputStream(entry).use { input -> input.copyTo(out, 1024 * 1024) }
                        session.fsync(out)
                    }
                }
            }
            val callbackIntent = Intent(context, SplitInstallReceiver::class.java).apply {
                action = SplitInstallReceiver.ACTION_INSTALL_STATUS
                putExtra(SplitInstallReceiver.EXTRA_SOURCE, container.absolutePath)
            }
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
            val pending = PendingIntent.getBroadcast(context, sessionId, callbackIntent, flags)
            session.commit(pending.intentSender)
        }
        return sessionId
    }

    fun zipAlign(input: File, output: File, alignment: Int = 4, soAlignment: Int = 16384, force: Boolean = true, verifyOnly: Boolean = false): Boolean {
        val a = validPowerOfTwo(alignment, 4)
        val so = if (soAlignment == 0) 0 else validPowerOfTwo(soAlignment, 16384)
        return if (verifyOnly) ZipAlign.isZipAligned(input.absolutePath, a, so)
        else ZipAlign.doZipAlign(input.absolutePath, output.absolutePath, a, so, force)
    }

    private fun extractEntry(container: File, path: String, output: File): File {
        output.parentFile?.let { if (!it.isDirectory) it.mkdirs() }
        ZipFile(container).use { zip ->
            val entry = zip.getEntry(path) ?: throw IOException("Entry not found: $path")
            zip.getInputStream(entry).use { input -> FileOutputStream(output).buffered().use { outputStream -> input.copyTo(outputStream) } }
        }
        return output
    }

    /**
     * Conservative fallback merger used when no universal APK exists. The base APK owns the
     * manifest/resource table. Code, native libraries and non-conflicting payload files from
     * selected splits are folded into it. META-INF signing entries are removed by default because
     * any merge invalidates the original signatures.
     */
    private fun mergeCompatibility(container: File, apks: List<SplitArchiveSupport.ApkEntry>, output: File, options: ConvertOptions) {
        val base = apks.firstOrNull { it.preferred } ?: apks.first()
        val tempDir = uniqueDirectory(output.parentFile ?: container.parentFile, ".split-merge-${System.nanoTime()}")
        if (!tempDir.mkdirs()) throw IOException("Cannot create merge workspace")
        try {
            val extracted = mutableListOf<Pair<SplitArchiveSupport.ApkEntry, File>>()
            ZipFile(container).use { outer ->
                apks.forEachIndexed { index, item ->
                    val temp = File(tempDir, "split-$index.apk")
                    val outerEntry = outer.getEntry(item.path) ?: return@forEachIndexed
                    outer.getInputStream(outerEntry).use { input -> temp.outputStream().buffered().use { out -> input.copyTo(out) } }
                    extracted += item to temp
                }
            }
            val baseFile = extracted.first { it.first.path == base.path }.second
            val used = hashSetOf<String>()
            val dexNames = hashSetOf<String>()
            var nextDex = 2
            ZipOutputStream(BufferedOutputStream(FileOutputStream(output))).use { zout ->
                zout.setLevel(options.compressionLevel.coerceIn(0, 9))
                fun copyZip(source: File, isBase: Boolean) {
                    ZipFile(source).use { zin ->
                        zin.entries().asSequence().filter { !it.isDirectory }.forEach { e ->
                            var name = e.name
                            val lower = name.lowercase(Locale.ROOT)
                            if (options.cleanMetaInf && lower.startsWith("meta-inf/")) return@forEach
                            if (!isBase) {
                                if (lower == "androidmanifest.xml" || lower == "resources.arsc") return@forEach
                                val always = lower.startsWith("lib/") || lower.matches(Regex("classes(\\d+)?\\.dex"))
                                val optional = lower.startsWith("assets/") || lower.startsWith("res/") || lower.startsWith("unknown/") || lower.startsWith("kotlin/")
                                if (!always && !(options.includeOptionalSplits && optional)) return@forEach
                                if (lower.matches(Regex("classes(\\d+)?\\.dex"))) {
                                    if (name in dexNames || name in used) {
                                        while ("classes${nextDex}.dex" in used) nextDex++
                                        name = "classes${nextDex++}.dex"
                                    }
                                    dexNames += name
                                } else if (name in used) return@forEach
                            }
                            if (name in used) return@forEach
                            used += name
                            val outEntry = ZipEntry(name).apply { time = e.time }
                            zout.putNextEntry(outEntry)
                            zin.getInputStream(e).use { it.copyTo(zout, 1024 * 1024) }
                            zout.closeEntry()
                        }
                    }
                }
                copyZip(baseFile, true)
                extracted.filterNot { it.second == baseFile }.forEach { copyZip(it.second, false) }
            }
        } finally {
            tempDir.deleteRecursively()
        }
    }

    private fun isConfigurationSplit(name: String): Boolean =
        name.startsWith("config.") || name.startsWith("config_") || name.startsWith("split_config") || name.contains("-config.")

    private fun languageQualifierFromName(name: String): String? {
        val stem = name.removeSuffix(".apk")
        val tokens = stem.split('.', '-', '_').filter { it.isNotBlank() }
        val skip = setOf("base", "config", "split", "master", "apk", "feature")
        for (i in tokens.indices) {
            val token = tokens[i].lowercase(Locale.ROOT)
            if (token in skip) continue
            if (token.matches(Regex("[a-z]{2,3}")) && token !in setOf("dpi", "arm", "x86")) {
                val next = tokens.getOrNull(i + 1)?.lowercase(Locale.ROOT)
                if (next != null && next.matches(Regex("r[a-z]{2}"))) return "$token-$next"
                return token
            }
        }
        return null
    }

    private fun densityQualifier(metrics: DisplayMetrics): String = when (metrics.densityDpi) {
        in 0..140 -> "ldpi"
        in 141..199 -> "mdpi"
        in 200..279 -> "hdpi"
        in 280..399 -> "xhdpi"
        in 400..559 -> "xxhdpi"
        else -> "xxxhdpi"
    }

    private fun normalizeToken(value: String): String = value.lowercase(Locale.ROOT).replace('-', '_').replace('.', '_')
    private fun validPowerOfTwo(value: Int, fallback: Int): Int = if (value > 0 && (value and (value - 1)) == 0) value else fallback

    private fun uniqueDirectory(parent: File, base: String): File {
        var candidate = File(parent, base)
        var i = 1
        while (candidate.exists()) candidate = File(parent, "$base-$i").also { i++ }
        return candidate
    }

    private fun uniqueFile(parent: File, name: String): File {
        if (!parent.exists()) parent.mkdirs()
        val stem = name.substringBeforeLast('.', name)
        val ext = name.substringAfterLast('.', "").let { if (it.isBlank()) "" else ".$it" }
        var candidate = File(parent, name)
        var i = 1
        while (candidate.exists()) candidate = File(parent, "$stem ($i)$ext").also { i++ }
        return candidate
    }
}
