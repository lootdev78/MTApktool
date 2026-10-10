package io.github.lootdev78.mtapktool.apktool

import android.content.Context
import android.util.Base64
import com.android.apksig.ApkVerifier
import com.reandroid.apk.APKLogger
import io.github.apktool.android.runtime.ApktoolCommandRunner
import io.github.lootdev78.mtapktool.apkeditor.ApkEditorEngine
import io.github.lootdev78.mtapktool.feature.explorer.util.deleteTreeSafely
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** Uses the same foreground worker pool as apktool-a; all mutable state is per job. */
object ApkEditorJobRunner {
    private val outputLocks = ConcurrentHashMap<String, Any>()

    fun run(
        context: Context,
        request: ApkEditorRequest,
        logger: APKLogger,
        cancelled: () -> Unit,
        stage: (ApktoolWorkflowStage, String) -> Unit,
        postProcess: (ApktoolCommandRunner.Result) -> ApktoolCommandRunner.Result,
        sign: Boolean,
    ): File {
        val input = File(request.input).canonicalFile
        val output = File(request.output).canonicalFile
        require(input != output) { "Die Originaldatei darf nicht überschrieben werden" }
        require(input.isFile && input.extension.equals("apk", true)) { "APK nicht gefunden" }
        require(output.extension.equals("apk", true)) { "Ausgabe muss eine .apk-Datei sein" }
        val parent = output.parentFile ?: error("Ausgabeordner fehlt")
        require(parent.isDirectory || parent.mkdirs()) { "Ausgabeordner kann nicht erstellt werden" }
        require(!request.confuseZip || !sign) { "ZIP-Verwirrung benötigt eine unsignierte Ausgabe" }
        synchronized(outputLocks.getOrPut(output.path) { Any() }) {
            require(!output.exists() || request.overwrite) { "Ausgabedatei existiert bereits" }
            val initialOutputHash = output.takeIf(File::isFile)?.let { hash(it, cancelled) }
            require(!output.exists() || output.isFile) { "Ausgabe ist kein reguläres File" }
            val initialInputHash = hash(input, cancelled)
            val work = File(context.cacheDir, "apk-editor/${UUID.randomUUID()}")
            check(work.mkdirs()) { "Arbeitsordner kann nicht erstellt werden" }
            var staged: File? = null
            var stagedV4: File? = null
            try {
                val snapshot = File(work, "input.apk")
                copy(input, snapshot, cancelled)
                check(hash(snapshot, cancelled) == initialInputHash) { "Eingabedatei wurde während des Lesens geändert" }
                val certificate = if (request.action == ApkEditorAction.KILL_SIGNATURE && request.killMethod == "MT") {
                    val info = ApkVerifier.Builder(snapshot).build().verify()
                    val cert = info.signerCertificates.firstOrNull()
                        ?: info.v3SchemeSigners.firstOrNull()?.certificate
                        ?: info.v2SchemeSigners.firstOrNull()?.certificate
                        ?: info.v1SchemeSigners.firstOrNull()?.certificate
                        ?: error("Originalzertifikat konnte nicht gelesen werden")
                    Base64.encodeToString(cert.encoded, Base64.NO_WRAP)
                } else ""
                stage(ApktoolWorkflowStage.EDITING, request.action.title)
                val unsigned = File(work, "result.apk")
                ApkEditorEngine.execute(snapshot, unsigned, request.engineOptions(), logger,
                    { path -> cancelled(); context.assets.open(path).use { it.readBytes() } }, certificate)
                cancelled()
                stage(ApktoolWorkflowStage.POST_PROCESSING, "Ausgabe ausrichten / signieren")
                val processed = postProcess(ApktoolCommandRunner.Result(0, request.action.title, unsigned))
                check(processed.isSuccess) { processed.summary }
                val ready = processed.output ?: error("Keine Ausgabedatei")
                stage(ApktoolWorkflowStage.VERIFYING, "APK-Ausgabe prüfen")
                ApkEditorEngine.validateApk(ready, request.confuseZip)
                if (sign) check(ApkVerifier.Builder(ready).build().verify().isVerified) { "APK-Signaturprüfung fehlgeschlagen" }
                check(hash(input, cancelled) == initialInputHash) { "Originaldatei hat sich geändert; Ergebnis wurde nicht veröffentlicht" }
                val stagedApk = File(parent, ".${output.name}.${UUID.randomUUID()}.part")
                staged = stagedApk
                copy(ready, stagedApk, cancelled)
                val idsig = File(ready.path + ".idsig")
                if (idsig.isFile) {
                    val sidecarStage = File(parent, ".${output.name}.${UUID.randomUUID()}.idsig.part")
                    stagedV4 = sidecarStage
                    copy(idsig, sidecarStage, cancelled)
                }
                cancelled()
                check(!output.exists() || output.isFile) { "Ausgabe wurde zwischenzeitlich durch einen Ordner ersetzt" }
                check(output.takeIf(File::isFile)?.let { hash(it, cancelled) } == initialOutputHash) { "Ausgabedatei wurde zwischenzeitlich geändert" }
                val outputV4 = File(output.path + ".idsig")
                check(!outputV4.exists() || request.overwrite) { "Eine .idsig-Datei existiert bereits" }
                // Publish under a lock and retain backups until both members are in place.
                val backup = File(parent, ".${output.name}.${UUID.randomUUID()}.backup")
                val backupV4 = File(parent, ".${output.name}.${UUID.randomUUID()}.idsig.backup")
                var oldApk = false; var oldV4 = false; var newApk = false; var newV4 = false
                try {
                    if (output.exists()) { Files.move(output.toPath(), backup.toPath()); oldApk = true }
                    if (outputV4.exists()) { Files.move(outputV4.toPath(), backupV4.toPath()); oldV4 = true }
                    Files.move(stagedApk.toPath(), output.toPath()); newApk = true
                    stagedV4?.let { Files.move(it.toPath(), outputV4.toPath()); newV4 = true }
                } catch (error: Exception) {
                    if (newV4) Files.deleteIfExists(outputV4.toPath())
                    if (newApk) Files.deleteIfExists(output.toPath())
                    if (oldApk) Files.move(backup.toPath(), output.toPath())
                    if (oldV4) Files.move(backupV4.toPath(), outputV4.toPath())
                    throw error
                }
                runCatching { Files.deleteIfExists(backup.toPath()); Files.deleteIfExists(backupV4.toPath()) }
                logger.logMessage("Ergebnis: ${output.path}")
                return output
            } finally {
                staged?.let { runCatching { Files.deleteIfExists(it.toPath()) } }
                stagedV4?.let { runCatching { Files.deleteIfExists(it.toPath()) } }
                runCatching { work.deleteTreeSafely() }
            }
        }
    }

    private fun copy(source: File, target: File, cancelled: () -> Unit) {
        source.inputStream().use { input -> target.outputStream().use { output ->
            val buffer = ByteArray(65536)
            while (true) { cancelled(); val size = input.read(buffer); if (size < 0) break; output.write(buffer, 0, size) }
        } }
    }
    private fun hash(file: File, cancelled: () -> Unit): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(65536)
            while (true) { cancelled(); val size = input.read(buffer); if (size < 0) break; digest.update(buffer, 0, size) }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
