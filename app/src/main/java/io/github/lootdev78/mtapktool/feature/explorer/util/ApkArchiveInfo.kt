package io.github.lootdev78.mtapktool.feature.explorer.util

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import androidx.core.graphics.drawable.toBitmap
import com.android.apksig.ApkVerifier
import java.io.File
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.ZipFile

data class ApkArchiveInfo(
    val file: File,
    val label: String,
    val packageName: String,
    val versionName: String,
    val versionCode: Long,
    val minSdk: Int?,
    val targetSdk: Int?,
    val icon: Bitmap?,
    val signatureSchemes: String,
    val installedVersion: String?,
    val installedDataDir: String?,
    val externalDataDir: String?,
    val installedApkPath: String?,
    val installedUid: Int?,
    val firstInstallTime: Long?,
    val lastUpdateTime: Long?,
    val protection: String,
)

object ApkArchiveReader {
    private data class IconEntry(val modifiedAt: Long, val length: Long, val bitmap: Bitmap?)
    private val iconCache = ConcurrentHashMap<String, IconEntry>()

    fun icon(context: Context, file: File): Bitmap? {
        val cached = iconCache[file.absolutePath]
        if (cached != null && cached.modifiedAt == file.lastModified() && cached.length == file.length()) {
            return cached.bitmap
        }
        val bitmap = when (file.extension.lowercase(Locale.ROOT)) {
            "apk" -> loadApkIcon(context, file)
            "apks", "apkm", "xapk", "apkx" -> loadSplitContainerIcon(context, file)
            else -> null
        }
        iconCache[file.absolutePath] = IconEntry(file.lastModified(), file.length(), bitmap)
        return bitmap
    }

    private fun loadApkIcon(context: Context, file: File): Bitmap? {
        val info = archivePackageInfo(context.packageManager, file, 0) ?: return null
        val appInfo = info.applicationInfo ?: return null
        appInfo.sourceDir = file.absolutePath
        appInfo.publicSourceDir = file.absolutePath
        return runCatching { appInfo.loadIcon(context.packageManager).toBitmap(96, 96) }.getOrNull()
    }

    /** Best-effort launcher icon for APKS/APKM/XAPK/APKX using universal/base APK. */
    private fun loadSplitContainerIcon(context: Context, container: File): Bitmap? = runCatching {
        ZipFile(container).use { zip ->
            val entries = zip.entries().asSequence()
                .filter { !it.isDirectory && it.name.lowercase(Locale.ROOT).endsWith(".apk") }
                .toList()
            if (entries.isEmpty()) return@use null
            fun score(nameValue: String, size: Long): Long {
                val path = nameValue.lowercase(Locale.ROOT)
                val name = File(path).name
                var result = when {
                    name == "universal.apk" -> 1_000_000L
                    name == "base.apk" -> 950_000L
                    name == "base-master.apk" -> 925_000L
                    name.startsWith("base-") && !name.contains("config") -> 850_000L
                    else -> 500_000L
                }
                if (name.startsWith("config") || name.startsWith("split_config")) result -= 400_000L
                if (size > 0) result += minOf(100_000L, size / 1024L)
                return result
            }
            val entry = entries.maxByOrNull { score(it.name, it.size) } ?: return@use null
            val dir = File(context.cacheDir, "apk-icon-probe").apply { mkdirs() }
            val temp = File(dir, "${container.absolutePath.hashCode()}-${container.lastModified()}.apk")
            try {
                zip.getInputStream(entry).use { input -> temp.outputStream().buffered().use { output -> input.copyTo(output) } }
                loadApkIcon(context, temp)
            } finally {
                temp.delete()
            }
        }
    }.getOrNull()

    fun read(context: Context, file: File): ApkArchiveInfo? {
        val pm = context.packageManager
        val info = archivePackageInfo(pm, file, PackageManager.GET_SIGNING_CERTIFICATES) ?: return null
        val appInfo = info.applicationInfo ?: return null
        appInfo.sourceDir = file.absolutePath
        appInfo.publicSourceDir = file.absolutePath

        val label = runCatching { appInfo.loadLabel(pm).toString() }.getOrDefault(file.nameWithoutExtension)
        val bitmap = icon(context, file)
        val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode else {
            @Suppress("DEPRECATION")
            info.versionCode.toLong()
        }
        val minSdk = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) appInfo.minSdkVersion else null
        val targetSdk = appInfo.targetSdkVersion
        val schemes = verifySchemes(file)

        val installed = runCatching { installedPackageInfo(pm, info.packageName) }.getOrNull()
        val installedApp = installed?.applicationInfo
        val installedCode = installed?.let {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) it.longVersionCode else {
                @Suppress("DEPRECATION")
                it.versionCode.toLong()
            }
        }
        val installedVersion = installed?.let { "${it.versionName ?: "?"} (${installedCode ?: 0})" }

        return ApkArchiveInfo(
            file = file,
            label = label,
            packageName = info.packageName.orEmpty(),
            versionName = info.versionName.orEmpty().ifBlank { "-" },
            versionCode = versionCode,
            minSdk = minSdk,
            targetSdk = targetSdk,
            icon = bitmap,
            signatureSchemes = schemes,
            installedVersion = installedVersion,
            installedDataDir = installedApp?.dataDir,
            externalDataDir = info.packageName?.takeIf { it.isNotBlank() }?.let { "/storage/emulated/0/Android/data/$it" },
            installedApkPath = installedApp?.sourceDir,
            installedUid = installedApp?.uid,
            firstInstallTime = installed?.firstInstallTime,
            lastUpdateTime = installed?.lastUpdateTime,
            protection = detectProtection(file),
        )
    }


    private fun detectProtection(file: File): String = runCatching {
        java.util.zip.ZipFile(file).use { zip ->
            val names = zip.entries().asSequence().map { it.name.lowercase() }.toList()
            val hit = when {
                names.any { "libjiagu" in it || "jiagu" in it } -> "Qihoo/Jiagu"
                names.any { "libsecexe" in it || "bangcle" in it } -> "Bangcle"
                names.any { "libshell" in it || "secshell" in it } -> "Shell/Protector"
                names.any { "dexguard" in it } -> "DexGuard"
                names.any { "ijiami" in it } -> "iJiami"
                names.any { "libprotect" in it || "protect" in it && it.startsWith("lib/") } -> "Native protector"
                else -> null
            }
            hit ?: "Nicht erkannt"
        }
    }.getOrDefault("Nicht geprüft")

    private fun verifySchemes(file: File): String = runCatching {
        val result = ApkVerifier.Builder(file).build().verify()
        if (!result.isVerified) return@runCatching "Nicht verifiziert"
        buildList {
            if (result.isVerifiedUsingV1Scheme) add("V1")
            if (result.isVerifiedUsingV2Scheme) add("V2")
            if (result.isVerifiedUsingV3Scheme || result.isVerifiedUsingV31Scheme) add("V3")
            if (result.isVerifiedUsingV4Scheme) add("V4")
        }.distinct().joinToString(" + ").ifBlank { "Signiert" }
    }.getOrDefault("Unbekannt")

    private fun archivePackageInfo(pm: PackageManager, file: File, flags: Int): PackageInfo? {
        return if (Build.VERSION.SDK_INT >= 33) {
            pm.getPackageArchiveInfo(file.absolutePath, PackageManager.PackageInfoFlags.of(flags.toLong()))
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageArchiveInfo(file.absolutePath, flags)
        }
    }

    private fun installedPackageInfo(pm: PackageManager, packageName: String): PackageInfo {
        return if (Build.VERSION.SDK_INT >= 33) {
            pm.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(packageName, 0)
        }
    }
}

fun sdkLabel(api: Int?): String = when (api) {
    null, 0 -> "-"
    29 -> "Android 10 (API 29)"
    30 -> "Android 11 (API 30)"
    31 -> "Android 12 (API 31)"
    32 -> "Android 12L (API 32)"
    33 -> "Android 13 (API 33)"
    34 -> "Android 14 (API 34)"
    35 -> "Android 15 (API 35)"
    36 -> "Android 16 (API 36)"
    else -> "API $api"
}
