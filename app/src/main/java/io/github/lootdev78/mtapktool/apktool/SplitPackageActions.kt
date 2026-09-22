package io.github.lootdev78.mtapktool.apktool

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.widget.Toast
import io.github.lootdev78.mtapktool.antisplit.AntiSplitEngine
import io.github.apktool.android.runtime.Toolchain
import io.github.abdurazaaqmohammed.apksigner.SignWrapper
import androidx.core.content.IntentCompat
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.Locale
import java.util.zip.ZipFile

object SplitPackageActions {
    fun mergeToApk(
        container: File,
        selected: List<SplitArchiveSupport.ApkEntry>,
        output: File,
        compressionLevel: Int = 6,
        forceMerge: Boolean = false,
        cleanMetaInf: Boolean = true,
        onLine: (String) -> Unit = {},
    ): File {
        val options = AntiSplitEngine.Options().apply {
            this.compressionLevel = compressionLevel.coerceIn(0, 9)
            this.forceMerge = forceMerge
            this.cleanMetaInf = cleanMetaInf
        }
        return AntiSplitEngine.mergeContainer(container, output, selected.map { it.path }, options) { onLine(it) }
    }

    fun signMergedApk(context: Context, apk: File, onLine: (String) -> Unit = {}): File {
        if (!apk.isFile) throw IOException("APK not found: $apk")
        val signature = ApktoolSettings.signatureDefaults(context)
        if (!signature.v1 && !signature.v2 && !signature.v3 && !signature.v4) {
            throw IOException("At least one signing scheme must be enabled")
        }
        val toolchain = Toolchain(context)
        val keystore = if (signature.profile == "custom") File(signature.customKeystorePath) else toolchain.getDebugKeystore()
        if (!keystore.isFile) throw IOException("Signing keystore not found: $keystore")
        val password = if (signature.profile == "custom") signature.customKeystorePassword.ifBlank { "android" } else "android"
        val temp = File(apk.parentFile, ".${apk.nameWithoutExtension}.sign-${System.nanoTime()}.apk")
        val tempIdsig = File(temp.absolutePath + ".idsig")
        val finalIdsig = File(apk.absolutePath + ".idsig")
        onLine("Signing ${apk.name} …")
        SignWrapper(keystore.absolutePath, password, signature.v1, signature.v2, signature.v3, signature.v4).signApk(apk, temp)
        if (!temp.isFile || temp.length() == 0L) throw IOException("Signing produced no APK")
        if (!apk.delete()) throw IOException("Could not replace unsigned APK")
        if (!temp.renameTo(apk)) {
            temp.copyTo(apk, overwrite = true)
            temp.delete()
        }
        if (signature.v4 && tempIdsig.isFile) {
            if (finalIdsig.exists()) finalIdsig.delete()
            if (!tempIdsig.renameTo(finalIdsig)) {
                tempIdsig.copyTo(finalIdsig, overwrite = true)
                tempIdsig.delete()
            }
        }
        onLine("Signed: ${apk.name}")
        return apk
    }

    fun extract(container: File, selected: List<SplitArchiveSupport.ApkEntry>, outputDir: File): List<File> {
        if (!outputDir.exists() && !outputDir.mkdirs()) throw IOException("Cannot create ${outputDir.path}")
        val result = mutableListOf<File>()
        ZipFile(container).use { zip ->
            selected.forEach { item ->
                val entry = zip.getEntry(item.path) ?: throw IOException("Missing ${item.path}")
                val out = unique(outputDir, File(item.path).name)
                zip.getInputStream(entry).use { input -> FileOutputStream(out).use { input.copyTo(it, 1024 * 1024) } }
                result += out
            }
        }
        return result
    }

    fun install(context: Context, container: File, selected: List<SplitArchiveSupport.ApkEntry>) {
        if (selected.isEmpty()) throw IOException("No APKs selected")
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        val sessionId = installer.createSession(params)
        val session = installer.openSession(sessionId)
        try {
            ZipFile(container).use { zip ->
                selected.forEachIndexed { index, item ->
                    val entry = zip.getEntry(item.path) ?: throw IOException("Missing ${item.path}")
                    val safeName = File(item.path).name.ifBlank { "split-$index.apk" }
                    session.openWrite(safeName, 0L, entry.size.coerceAtLeast(0L)).use { out ->
                        zip.getInputStream(entry).use { input -> input.copyTo(out, 1024 * 1024) }
                        session.fsync(out)
                    }
                }
            }
            val intent = Intent(context, SplitInstallReceiver::class.java).apply {
                action = "${context.packageName}.SPLIT_INSTALL_STATUS"
                putExtra("sessionId", sessionId)
            }
            val mutableFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
            val pending = PendingIntent.getBroadcast(
                context,
                sessionId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or mutableFlag,
            )
            session.commit(pending.intentSender)
        } finally {
            session.close()
        }
    }

    private fun unique(dir: File, name: String): File {
        var out = File(dir, name)
        if (!out.exists()) return out
        val ext = name.substringAfterLast('.', "").let { if (it.isBlank()) "" else ".$it" }
        val base = if (ext.isBlank()) name else name.dropLast(ext.length)
        var i = 1
        while (out.exists()) out = File(dir, "$base ($i)${ext.lowercase(Locale.ROOT)}").also { i++ }
        return out
    }
}

class SplitInstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirm = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_INTENT, Intent::class.java)
                if (confirm != null) {
                    confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(confirm)
                }
            }
            PackageInstaller.STATUS_SUCCESS -> Toast.makeText(context, "Installation erfolgreich", Toast.LENGTH_LONG).show()
            else -> Toast.makeText(context, message ?: "Installation fehlgeschlagen", Toast.LENGTH_LONG).show()
        }
    }
}
