package io.github.lootdev78.mtapktool.feature.explorer.util

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import androidx.core.graphics.drawable.toBitmap
import androidx.core.content.pm.PackageInfoCompat
import com.android.apksig.ApkVerifier
import java.io.File
import java.security.MessageDigest
import java.security.interfaces.RSAKey
import java.security.interfaces.ECKey
import java.util.zip.CRC32
import java.util.concurrent.ConcurrentHashMap
import java.util.Locale

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
)


data class ApkSignatureInfo(
    val schemes: String,
    val status: String,
    val algorithm: String,
    val publicKey: String,
    val validFrom: Long,
    val validUntil: Long,
    val owner: String,
    val hash: String,
    val crc32: String,
    val md5: String,
    val sha1: String,
    val sha256: String,
    val rawCertificateHex: String,
)

object ApkArchiveReader {
    private data class IconEntry(val modifiedAt: Long, val length: Long, val bitmap: Bitmap?)
    private val iconCache = ConcurrentHashMap<String, IconEntry>()

    fun icon(context: Context, file: File): Bitmap? {
        val cached = iconCache[file.absolutePath]
        if (cached != null && cached.modifiedAt == file.lastModified() && cached.length == file.length()) {
            return cached.bitmap
        }
        val info = archivePackageInfo(context.packageManager, file, 0) ?: return null
        val appInfo = info.applicationInfo ?: return null
        appInfo.sourceDir = file.absolutePath
        appInfo.publicSourceDir = file.absolutePath
        val bitmap = runCatching { appInfo.loadIcon(context.packageManager).toBitmap(96, 96) }.getOrNull()
        iconCache[file.absolutePath] = IconEntry(file.lastModified(), file.length(), bitmap)
        return bitmap
    }

    fun read(context: Context, file: File): ApkArchiveInfo? {
        val pm = context.packageManager
        val info = archivePackageInfo(pm, file, PackageManager.GET_SIGNING_CERTIFICATES) ?: return null
        val appInfo = info.applicationInfo ?: return null
        appInfo.sourceDir = file.absolutePath
        appInfo.publicSourceDir = file.absolutePath

        val label = runCatching { appInfo.loadLabel(pm).toString() }.getOrDefault(file.nameWithoutExtension)
        val bitmap = icon(context, file)
        val versionCode = PackageInfoCompat.getLongVersionCode(info)
        val minSdk = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) appInfo.minSdkVersion else null
        val targetSdk = appInfo.targetSdkVersion
        val schemes = verifySchemes(file)

        val installed = runCatching { installedPackageInfo(pm, info.packageName) }.getOrNull()
        val installedApp = installed?.applicationInfo
        val installedCode = installed?.let(PackageInfoCompat::getLongVersionCode)
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
            externalDataDir = info.packageName.takeIf { it.isNotBlank() }?.let { "/storage/emulated/0/Android/data/$it" },
            installedApkPath = installedApp?.sourceDir,
            installedUid = installedApp?.uid,
            firstInstallTime = installed?.firstInstallTime,
            lastUpdateTime = installed?.lastUpdateTime,
        )
    }

    fun signatureInfo(file: File, addColons: Boolean = true, upperCase: Boolean = true): ApkSignatureInfo? = runCatching {
        val result = ApkVerifier.Builder(file).build().verify()
        val cert = result.signerCertificates.firstOrNull() ?: return@runCatching null
        val bytes = cert.encoded
        fun digest(name: String): String {
            val raw = MessageDigest.getInstance(name).digest(bytes).joinToString("") { "%02x".format(it) }
            val cased = if (upperCase) raw.uppercase(Locale.ROOT) else raw.lowercase(Locale.ROOT)
            return if (addColons) cased.chunked(2).joinToString(":") else cased
        }
        val key = cert.publicKey
        val bitCount = when (key) {
            is RSAKey -> key.modulus.bitLength()
            is ECKey -> key.params.order.bitLength()
            else -> key.encoded.size * 8
        }
        val crc = CRC32().apply { update(bytes) }.value
        val rawHex = bytes.joinToString("") { "%02X".format(it) }
        ApkSignatureInfo(
            schemes = buildList {
                if (result.isVerifiedUsingV1Scheme) add("V1")
                if (result.isVerifiedUsingV2Scheme) add("V2")
                if (result.isVerifiedUsingV3Scheme || result.isVerifiedUsingV31Scheme) add("V3")
                if (result.isVerifiedUsingV4Scheme) add("V4")
            }.distinct().joinToString(" + ").ifBlank { "-" },
            status = if (result.isVerified) "Verified successfully" else "Verification failed",
            algorithm = cert.sigAlgName ?: key.algorithm,
            publicKey = "${key.algorithm} · $bitCount bits",
            validFrom = cert.notBefore.time,
            validUntil = cert.notAfter.time,
            owner = cert.subjectX500Principal.name,
            hash = "0x${cert.hashCode().toUInt().toString(16).uppercase(Locale.ROOT)} (${cert.hashCode()})",
            crc32 = "0x${crc.toString(16).uppercase(Locale.ROOT)} ($crc)",
            md5 = digest("MD5"),
            sha1 = digest("SHA-1"),
            sha256 = digest("SHA-256"),
            rawCertificateHex = rawHex,
        )
    }.getOrNull()

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
        if (Build.VERSION.SDK_INT >= 33) {
            return pm.getPackageArchiveInfo(file.absolutePath, PackageManager.PackageInfoFlags.of(flags.toLong()))
        }
        val method = PackageManager::class.java.getMethod(
            "getPackageArchiveInfo",
            String::class.java,
            Int::class.javaPrimitiveType,
        )
        return method.invoke(pm, file.absolutePath, flags) as? PackageInfo
    }

    private fun installedPackageInfo(pm: PackageManager, packageName: String): PackageInfo {
        if (Build.VERSION.SDK_INT >= 33) {
            return pm.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
        }
        val method = PackageManager::class.java.getMethod(
            "getPackageInfo",
            String::class.java,
            Int::class.javaPrimitiveType,
        )
        return method.invoke(pm, packageName, 0) as PackageInfo
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
