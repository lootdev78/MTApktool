package io.github.lootdev78.mtapktool.feature.ftp

import io.github.lootdev78.mtapktool.feature.explorer.util.deleteTreeSafely

import android.content.Context
import android.net.Uri
import io.github.lootdev78.mtftp.FtpPaths
import io.github.lootdev78.mtftp.MtFtpClient
import io.github.lootdev78.mtapktool.feature.explorer.model.FileItem
import io.github.lootdev78.mtapktool.feature.explorer.saf.SafFileSystem
import io.github.lootdev78.mtapktool.feature.explorer.viewmodel.FileConflictAction
import kotlinx.coroutines.CancellationException
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class NetworkFileOperations(private val context: Context) {
    data class Connection(val client: MtFtpClient, val profile: FtpProfile)
    private val connections = ConcurrentHashMap<String, Connection>()
    fun connect(profile: FtpProfile, password: String): String {
        val client = MtFtpClient()
        val path = client.connect(profile.host, profile.port, profile.username, password, profile.security, profile.directory)
        val id = UUID.randomUUID().toString()
        connections[id] = Connection(client, profile)
        return FtpLocation.uri(id, path)
    }
    fun connection(path: String) = connections[FtpLocation.session(path)] ?: throw IOException("FTP-Verbindung beendet; Panel neu verbinden")
    fun list(path: String) = connection(path).client.list(FtpLocation.remotePath(path)).map { FtpLocation.item(FtpLocation.session(path), it) }
    fun display(path: String) = FtpLocation.display(connection(path).profile, path)
    fun disconnect(path: String) { connections.remove(FtpLocation.session(path))?.client?.close() }
    fun abortAll() { connections.values.forEach { it.client.abortConnection() } }
    fun abort(path: String) { if (FtpLocation.isRemote(path)) connections[FtpLocation.session(path)]?.client?.abortConnection() }
    fun close() { connections.values.forEach { it.client.abortConnection() }; connections.clear() }
    private fun sameEndpoint(first: String, second: String): Boolean {
        val a = connection(first).profile; val b = connection(second).profile
        return a.host.equals(b.host, true) && a.port == b.port && a.username == b.username && a.security == b.security
    }
    fun sameSource(first: FileItem, second: FileItem): Boolean = runCatching {
        when {
            first.isFtp && second.isFtp -> sameEndpoint(first.path, second.path) && FtpLocation.remotePath(first.path) == FtpLocation.remotePath(second.path)
            first.isSaf && second.isSaf -> {
                val a = Uri.parse(first.path); val b = Uri.parse(second.path)
                a.authority == b.authority && android.provider.DocumentsContract.getDocumentId(a) == android.provider.DocumentsContract.getDocumentId(b)
            }
            else -> first.path == second.path
        }
    }.getOrDefault(false)
    fun uploadEdited(item: FileItem, file: File, expectedHash: String, checkCancelled: () -> Unit) {
        val current = materialize(item) { checkCancelled() }
        try {
            if (sha256(current) != expectedHash) throw IOException("Die FTP-Datei wurde inzwischen auf dem Server geändert. Die lokale Änderung bleibt erhalten.")
        } finally { current.parentFile?.deleteTreeSafely() }
        checkCancelled()
        file.inputStream().use { source -> connection(item.path).client.upload(FtpLocation.remotePath(item.path), source, true) { checkCancelled() } }
    }
    fun uploadDocumentEdited(item: FileItem, file: File, parent: String, expectedHash: String, checkCancelled: () -> Unit): Uri {
        require(item.isSaf)
        val original = SafFileSystem.list(context, Uri.parse(parent)).firstOrNull { it.path == item.path }
            ?: throw IOException("Originaldokument wurde verschoben oder entfernt; lokale Änderung bleibt erhalten")
        return publishSaf(file, parent, item.name, original) {
            val digest = java.security.MessageDigest.getInstance("SHA-256")
            context.contentResolver.openInputStream(Uri.parse(original.path))?.use { input ->
                val buffer = ByteArray(128 * 1024)
                while (true) { checkCancelled(); val n = input.read(buffer); if (n < 0) break; if (n > 0) digest.update(buffer, 0, n) }
            } ?: throw IOException("Originaldokument kann nicht geprüft werden")
            val hash = digest.digest().joinToString("") { "%02x".format(it) }
            if (hash != expectedHash) throw IOException("Originaldokument wurde inzwischen geändert; lokale Änderung bleibt erhalten")
            checkCancelled()
        }
    }
    fun sha256(file: File): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input -> val buffer = ByteArray(128 * 1024); while (true) { val n = input.read(buffer); if (n < 0) break; digest.update(buffer, 0, n) } }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
    fun rename(path: String, name: String) {
        FtpPaths.name(name)
        connection(path).client.rename(FtpLocation.remotePath(path), FtpPaths.child(FtpPaths.parent(FtpLocation.remotePath(path)), name))
    }
    fun delete(path: String) = connection(path).client.delete(FtpLocation.remotePath(path))
    fun create(parent: String, name: String, directory: Boolean) {
        val path = FtpPaths.child(FtpLocation.remotePath(parent), name)
        val client = connection(parent).client
        if (client.stat(path) != null) throw IOException("Name existiert bereits")
        if (directory) client.mkdir(path) else java.io.ByteArrayInputStream(byteArrayOf()).use { client.upload(path, it, false) {} }
    }
    fun materialize(item: FileItem, onBytes: (Long) -> Unit = {}): File {
        require(item.isFtp && !item.isDirectory)
        val directory = File(context.cacheDir, "ftp-open/${UUID.randomUUID()}")
        if (!directory.mkdirs()) throw IOException("Temporärer Ordner konnte nicht erstellt werden")
        val output = File(directory, FtpPaths.name(item.name))
        try { output.outputStream().use { connection(item.path).client.download(FtpLocation.remotePath(item.path), it) { n -> onBytes(n) } } }
        catch (e: Throwable) { directory.deleteTreeSafely(); throw e }
        return output
    }

    suspend fun copy(
        item: FileItem,
        targetDirectory: String,
        conflict: suspend (FileItem, String) -> FileConflictAction,
        onBytes: (Long) -> Unit,
        checkCancelled: () -> Unit,
        depth: Int = 0,
    ): Boolean {
        checkCancelled()
        if (depth > 100) throw IOException("Verzeichnisstruktur zu tief")
        if (!item.isSaf && !item.isFtp && Files.isSymbolicLink(item.file.toPath())) throw IOException("Symbolische Verknüpfung wird nicht übertragen: ${item.name}")
        FtpPaths.name(item.name)
        if (item.isDirectory && item.isSaf && SafFileSystem.isSafPath(targetDirectory)) {
            val source = Uri.parse(item.path); val target = Uri.parse(targetDirectory)
            if (source.authority == target.authority) {
                val sourceId = android.provider.DocumentsContract.getDocumentId(source)
                val targetId = android.provider.DocumentsContract.getDocumentId(target)
                val child = runCatching { android.provider.DocumentsContract.isChildDocument(context.contentResolver, source, target) }.getOrDefault(false)
                if (sourceId == targetId || child || targetId.startsWith(sourceId.trimEnd('/') + "/")) throw IOException("Zielordner liegt innerhalb des Quelldokuments")
            }
        }
        var name = item.name
        var existing = childItem(targetDirectory, name)
        var replace = false
        var destination = childPath(targetDirectory, name, existing)
        if (item.path == destination || (FtpLocation.isRemote(item.path) && FtpLocation.isRemote(destination) && sameEndpoint(item.path, destination) &&
                    (FtpLocation.remotePath(destination) == FtpLocation.remotePath(item.path) || (item.isDirectory && FtpLocation.remotePath(destination).startsWith(FtpLocation.remotePath(item.path).trimEnd('/') + "/")))))
            throw IOException("Quelle und Ziel sind identisch oder das Ziel liegt in der Quelle")
        if (!item.isFtp && !item.isSaf && !FtpLocation.isRemote(destination) && !SafFileSystem.isSafPath(destination)) {
            val source = item.file.canonicalFile; val target = File(destination).canonicalFile
            if (source == target || (source.isDirectory && target.path.startsWith(source.path + File.separator))) throw IOException("Ziel liegt in der Quelle")
        }
        if (existing != null) when (conflict(item, destination)) {
            FileConflictAction.CANCEL -> throw CancellationException("Übertragung abgebrochen")
            FileConflictAction.SKIP -> return false
            FileConflictAction.KEEP_BOTH -> {
                val dot = name.lastIndexOf('.').takeIf { it > 0 && !item.isDirectory } ?: name.length
                val base = name.substring(0, dot); val extension = name.substring(dot)
                var index = 1
                while (childItem(targetDirectory, name) != null) { name = "$base (${index++})$extension" }
                existing = null; destination = childPath(targetDirectory, name, null)
            }
            FileConflictAction.OVERWRITE -> {
                if (existing!!.isDirectory != item.isDirectory) throw IOException("Datei und Ordner können nicht gegenseitig ersetzt werden")
                replace = true
            }
        }
        if (item.isDirectory) {
            val directory = if (existing != null) existing!!.path else createDirectory(targetDirectory, name)
            var complete = true
            for (child in children(item)) {
                // A skipped child prevents deleting this source directory during a MOVE.
                if (!copy(child, directory, conflict, onBytes, checkCancelled, depth + 1)) complete = false
            }
            if (existing == null && !FtpLocation.isRemote(directory) && !SafFileSystem.isSafPath(directory)) {
                val target = File(directory)
                if (io.github.lootdev78.mtapktool.settings.ExplorerPreferences.current(context).preserveFileTime && item.modifiedAt > 0) target.setLastModified(item.modifiedAt)
                item.unixMode?.let { mode -> runCatching { android.system.Os.chmod(target.absolutePath, mode and 0xFFF) } }
            }
            return complete
        }
        val staging = File.createTempFile("mt-transfer-", ".tmp", context.cacheDir)
        try {
            staging.outputStream().use { output ->
                if (item.isFtp) connection(item.path).client.download(FtpLocation.remotePath(item.path), output) { count -> checkCancelled(); onBytes(count) }
                else input(item).use { source -> copyStream(source, output, checkCancelled, onBytes) }
            }
            checkCancelled()
            when {
                FtpLocation.isRemote(targetDirectory) -> staging.inputStream().use { source ->
                    connection(targetDirectory).client.upload(FtpPaths.child(FtpLocation.remotePath(targetDirectory), name), source, replace) { count -> checkCancelled(); onBytes(count) }
                }
                SafFileSystem.isSafPath(targetDirectory) -> publishSaf(staging, targetDirectory, name, existing) { checkCancelled() }
                else -> {
                    val target = File(targetDirectory, name)
                    val temporary = File(targetDirectory, ".mt-transfer-${UUID.randomUUID()}")
                    try {
                        staging.inputStream().use { source -> temporary.outputStream().use { output -> copyStream(source, output, checkCancelled, onBytes) } }
                        if (io.github.lootdev78.mtapktool.settings.ExplorerPreferences.current(context).preserveFileTime && item.modifiedAt > 0) temporary.setLastModified(item.modifiedAt)
                        item.unixMode?.let { mode -> runCatching { android.system.Os.chmod(temporary.absolutePath, mode and 0xFFF) } }
                        if (!replace && target.exists()) throw IOException("Ziel wurde zwischenzeitlich erstellt")
                        val options = if (replace) arrayOf(StandardCopyOption.REPLACE_EXISTING) else emptyArray()
                        Files.move(temporary.toPath(), target.toPath(), *options)
                    } finally { temporary.delete() }
                }
            }
            return true
        } finally { staging.delete() }
    }
    private fun copyStream(input: InputStream, output: OutputStream, checkCancelled: () -> Unit, progress: (Long) -> Unit) {
        val buffer = ByteArray(128 * 1024); var total = 0L
        while (true) {
            checkCancelled()
            val read = input.read(buffer); if (read < 0) break
            if (read > 0) { output.write(buffer, 0, read); total += read; progress(total) }
        }
        output.flush()
    }
    private fun input(item: FileItem): InputStream = if (item.isSaf)
        context.contentResolver.openInputStream(Uri.parse(item.path)) ?: throw IOException("Quelldokument nicht lesbar")
    else item.file.inputStream()
    private fun children(item: FileItem): List<FileItem> = when {
        item.isFtp -> list(item.path)
        item.isSaf -> SafFileSystem.list(context, Uri.parse(item.path))
        else -> item.file.listFiles()?.map { FileItem(it) } ?: throw IOException("Ordner nicht lesbar: ${item.name}")
    }
    private fun childItem(parent: String, name: String): FileItem? = when {
        FtpLocation.isRemote(parent) -> connection(parent).client.stat(FtpPaths.child(FtpLocation.remotePath(parent), name))?.let { FtpLocation.item(FtpLocation.session(parent), it) }
        SafFileSystem.isSafPath(parent) -> SafFileSystem.list(context, Uri.parse(parent)).firstOrNull { it.name == name }
        else -> File(parent, name).takeIf { it.exists() }?.let { FileItem(it) }
    }
    private fun childPath(parent: String, name: String, existing: FileItem?) = existing?.path ?: when {
        FtpLocation.isRemote(parent) -> FtpLocation.child(parent, name)
        SafFileSystem.isSafPath(parent) -> "$parent/$name"
        else -> File(parent, name).absolutePath
    }
    private fun createDirectory(parent: String, name: String): String = when {
        FtpLocation.isRemote(parent) -> FtpLocation.child(parent, name).also { connection(parent).client.mkdir(FtpLocation.remotePath(it)) }
        SafFileSystem.isSafPath(parent) -> SafFileSystem.create(context, Uri.parse(parent), name, true).toString()
        else -> File(parent, name).also { if (!it.mkdir()) throw IOException("Ordner konnte nicht erstellt werden") }.absolutePath
    }
    private fun publishSaf(staged: File, parent: String, name: String, existing: FileItem?, beforePublish: () -> Unit = {}): Uri {
        val type = existing?.mimeType ?: android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(name.substringAfterLast('.', "").lowercase()) ?: "application/octet-stream"
        var uri = SafFileSystem.create(context, Uri.parse(parent), ".mt-transfer-${UUID.randomUUID()}", false, type)
        var published = false
        var backup: Uri? = null
        try {
            context.contentResolver.openOutputStream(uri, "wt")?.use { output -> staged.inputStream().use { it.copyTo(output) } }
                ?: throw IOException("Zieldokument nicht beschreibbar")
            beforePublish()
            if (existing != null) backup = SafFileSystem.rename(context, Uri.parse(existing.path), ".mt-backup-${UUID.randomUUID()}")
            uri = SafFileSystem.rename(context, uri, name)
            if (SafFileSystem.documentName(context, uri) != name) throw IOException("Dokumentanbieter hat den Zielnamen verändert")
            published = true
            backup?.let { runCatching { SafFileSystem.delete(context, it) } }
            return uri
        } catch (error: Throwable) {
            if (!published) backup?.let { backupUri ->
                try { SafFileSystem.rename(context, backupUri, name) }
                catch (rollback: Throwable) { error.addSuppressed(IOException("Originalsicherung bleibt unter $backupUri", rollback)) }
            }
            throw error
        } finally { if (!published) runCatching { SafFileSystem.delete(context, uri) } }
    }
}
