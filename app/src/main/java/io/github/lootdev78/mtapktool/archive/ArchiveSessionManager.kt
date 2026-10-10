package io.github.lootdev78.mtapktool.archive

import io.github.lootdev78.mtapktool.feature.explorer.util.deleteTreeSafely

import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

enum class ArchiveSessionState { OPENING, CLEAN, DIRTY, UPDATING, FAILED, CLOSED }

data class ArchiveEntryStamp(
    val directory: Boolean,
    val size: Long,
    val modifiedAt: Long,
    val digestHint: String,
    val mode: Int?,
)

data class ArchiveSessionSnapshot(
    val id: String,
    val archive: File,
    val workspaceRoot: File,
    val returnDirectory: String,
    val parentId: String?,
    val state: ArchiveSessionState,
    val depth: Int,
    val dirtyEntries: Set<String>,
    val lastError: String?,
    val charset: String = "",
)

class ArchiveSessionManager(private val cacheRoot: File) {
    private data class MutableSession(
        val id: String,
        val archive: File,
        val workspaceRoot: File,
        val returnDirectory: String,
        val password: String,
        var charset: String,
        val parentId: String?,
        val depth: Int,
        var baseline: Map<String, ArchiveEntryStamp>,
        @Volatile var state: ArchiveSessionState,
        var sourceDigest: String,
        var dirtyEntries: Set<String> = emptySet(),
        var lastError: String? = null,
    )

    private val sessions = ConcurrentHashMap<String, MutableSession>()
    private val passwordVault = ConcurrentHashMap<String, String>()
    private val charsetVault = ConcurrentHashMap<String, String>()

    fun cachedPassword(archive: File): String? = runCatching { passwordVault[archive.canonicalPath] }.getOrNull()

    @Synchronized fun open(
        archive: File,
        returnDirectory: String,
        password: String = "",
        parentId: String? = null,
        charset: String? = null,
        onProgress: (Int) -> Unit = {},
    ): ArchiveSessionSnapshot {
        if (!ArchiveEngine.supports(archive)) throw IOException("Unsupported archive: ${archive.name}")
        sessions.values.firstOrNull {
            it.archive == archive.canonicalFile && it.parentId == parentId && it.state != ArchiveSessionState.CLOSED
        }?.let { refreshState(it); return it.toSnapshot() }
        if (!cacheRoot.exists() && !cacheRoot.mkdirs()) throw IOException("Cannot create archive workspace root")
        val parent = parentId?.let(sessions::get)
        val effectivePassword = password.ifBlank { cachedPassword(archive).orEmpty() }
        val id = UUID.randomUUID().toString()
        val workspace = File(cacheRoot, "${archive.name.hashCode()}-${id.take(8)}").canonicalFile
        if (!workspace.mkdirs()) throw IOException("Cannot create archive workspace: ${workspace.absolutePath}")
        val session = MutableSession(
            id = id,
            archive = archive.canonicalFile,
            workspaceRoot = workspace,
            returnDirectory = returnDirectory,
            password = effectivePassword,
            charset = charset ?: charsetVault[archive.canonicalPath].orEmpty(),
            parentId = parentId,
            depth = (parent?.depth ?: -1) + 1,
            baseline = emptyMap(),
            state = ArchiveSessionState.OPENING,
            sourceDigest = digestHint(archive),
        )
        sessions[id] = session
        try {
            ArchiveEngine.extractToDirectory(session.archive, workspace, effectivePassword, onProgress, charset = session.charset)
            session.baseline = snapshot(workspace)
            session.state = ArchiveSessionState.CLEAN
            session.lastError = null
            if (effectivePassword.isNotBlank()) passwordVault[session.archive.canonicalPath] = effectivePassword
            return session.toSnapshot()
        } catch (t: Throwable) {
            session.state = ArchiveSessionState.FAILED
            session.lastError = t.message ?: t.javaClass.simpleName
            if (effectivePassword.isNotBlank()) passwordVault.remove(session.archive.canonicalPath)
            workspace.deleteTreeSafely()
            sessions.remove(id)
            throw t
        }
    }

    fun peek(id: String): ArchiveSessionSnapshot? = sessions[id]?.toSnapshot()
    @Synchronized fun get(id: String): ArchiveSessionSnapshot? = sessions[id]?.also(::refreshState)?.toSnapshot()
    fun refresh(id: String): ArchiveSessionSnapshot? = get(id)

    @Synchronized fun markDirty(id: String, relativePath: String? = null): ArchiveSessionSnapshot? {
        val session = sessions[id] ?: return null
        if (session.state != ArchiveSessionState.CLOSED && session.state != ArchiveSessionState.UPDATING) {
            session.state = ArchiveSessionState.DIRTY
            if (!relativePath.isNullOrBlank()) session.dirtyEntries = session.dirtyEntries + relativePath
        }
        return session.toSnapshot()
    }

    @Synchronized fun commit(id: String, level: ArchiveLevel = ArchiveLevel.NORMAL, onProgress: (Int) -> Unit = {}): ArchiveSessionSnapshot {
        val session = sessions[id] ?: throw IOException("Archive session no longer exists")
        refreshState(session)
        if (session.state == ArchiveSessionState.CLEAN) return session.toSnapshot()
        if (session.state == ArchiveSessionState.CLOSED) throw IOException("Archive session is closed")
        session.state = ArchiveSessionState.UPDATING
        session.lastError = null
        return try {
            onProgress(0)
            if (digestHint(session.archive) != session.sourceDigest) throw IOException("Das Originalarchiv wurde extern geändert. Änderungen zuerst sichern und das Archiv neu öffnen.")
            ArchiveEngine.replaceFromDirectory(session.archive, session.workspaceRoot, session.password, level, session.charset) { onProgress(95) }
            onProgress(100)
            session.baseline = snapshot(session.workspaceRoot)
            session.sourceDigest = digestHint(session.archive)
            session.dirtyEntries = emptySet()
            session.state = ArchiveSessionState.CLEAN
            session.parentId?.let { parentId ->
                val parent = sessions[parentId]
                if (parent != null) {
                    parent.state = ArchiveSessionState.DIRTY
                    val relative = runCatching { session.archive.relativeTo(parent.workspaceRoot).invariantSeparatorsPath }.getOrNull()
                    if (!relative.isNullOrBlank()) parent.dirtyEntries = parent.dirtyEntries + relative
                }
            }
            session.toSnapshot()
        } catch (t: Throwable) {
            session.state = ArchiveSessionState.FAILED
            session.lastError = t.message ?: t.javaClass.simpleName
            throw t
        }
    }

    fun test(id: String, onProgress: (Int, String) -> Unit, checkCancelled: () -> Unit): ArchiveTestResult {
        val session = sessions[id] ?: throw IOException("Archiv ist nicht mehr geöffnet")
        checkCancelled()
        val before = digestHint(session.archive, checkCancelled)
        val result = ArchiveEngine.test(session.archive, session.password, session.charset, onProgress, checkCancelled)
        checkCancelled()
        if (digestHint(session.archive, checkCancelled) != before) throw IOException("Das Archiv wurde während des Tests geändert")
        checkCancelled()
        return result
    }

    /** Stages a fresh decoding and keeps the old workspace intact on failure/cancellation. */
    @Synchronized fun reloadCharset(id: String, charset: String, checkCancelled: () -> Unit = {}, onProgress: (Int) -> Unit): ArchiveSessionSnapshot {
        val session = sessions[id] ?: throw IOException("Archiv ist nicht mehr geöffnet")
        if (!ArchiveCharsets.supports(ArchiveFormat.fromFile(session.archive))) throw IOException("Dieses Format verwendet einen festen Zeichensatz")
        ArchiveCharsets.charset(charset)
        refreshState(session)
        if (session.dirtyEntries.isNotEmpty() || session.state == ArchiveSessionState.DIRTY) {
            throw IOException("Archivänderungen zuerst über Aktualisieren speichern")
        }
        if (session.state == ArchiveSessionState.OPENING || session.state == ArchiveSessionState.UPDATING || session.state == ArchiveSessionState.CLOSED) {
            throw IOException("Laufende Archiv-Aktion zuerst beenden")
        }
        if (sessions.values.any { it.parentId == id && it.state != ArchiveSessionState.CLOSED }) {
            throw IOException("Offene Unterarchive zuerst schließen")
        }
        val previousState = session.state
        val sourceDigest = digestHint(session.archive)
        val parent = session.workspaceRoot.parentFile ?: throw IOException("Arbeitsordner fehlt")
        val stage = File(parent, ".charset-" + UUID.randomUUID())
        val backup = File(parent, ".previous-" + UUID.randomUUID())
        if (!stage.mkdirs()) throw IOException("Arbeitsordner kann nicht erstellt werden")
        session.state = ArchiveSessionState.OPENING
        var movedOld = false
        try {
            ArchiveEngine.extractToDirectory(session.archive, stage, session.password, onProgress, charset = charset, checkCancelled = checkCancelled)
            val baseline = snapshot(stage)
            if (digestHint(session.archive, checkCancelled) != sourceDigest) throw IOException("Das Archiv wurde während des Neuladens geändert")
            if (snapshot(session.workspaceRoot) != session.baseline) throw IOException("Dateien im Archiv wurden während des Neuladens geändert")
            onProgress(99)
            Files.move(session.workspaceRoot.toPath(), backup.toPath())
            movedOld = true
            Files.move(stage.toPath(), session.workspaceRoot.toPath())
            session.baseline = baseline
            session.sourceDigest = sourceDigest
            session.charset = charset
            charsetVault[session.archive.canonicalPath] = charset
            session.dirtyEntries = emptySet()
            session.lastError = null
            session.state = ArchiveSessionState.CLEAN
            runCatching { backup.deleteTreeSafely() }
            return session.toSnapshot()
        } catch (error: Throwable) {
            if (movedOld && !session.workspaceRoot.exists()) Files.move(backup.toPath(), session.workspaceRoot.toPath())
            session.state = previousState
            refreshState(session)
            throw error
        } finally {
            runCatching { stage.deleteTreeSafely() }
        }
    }

    @Synchronized fun discard(id: String): ArchiveSessionSnapshot? {
        val session = sessions.remove(id) ?: return null
        session.state = ArchiveSessionState.CLOSED
        session.workspaceRoot.deleteTreeSafely()
        return session.toSnapshot()
    }

    @Synchronized fun closeAfterCommit(id: String): ArchiveSessionSnapshot? {
        val session = sessions.remove(id) ?: return null
        session.state = ArchiveSessionState.CLOSED
        session.workspaceRoot.deleteTreeSafely()
        return session.toSnapshot()
    }

    fun clearPassword(archive: File) { runCatching { passwordVault.remove(archive.canonicalPath) } }

    fun discardAll() {
        sessions.values.toList().forEach { it.workspaceRoot.deleteTreeSafely() }
        sessions.clear()
        passwordVault.clear()
        charsetVault.clear()
    }

    private fun refreshState(session: MutableSession) {
        if (session.state == ArchiveSessionState.CLOSED || session.state == ArchiveSessionState.OPENING || session.state == ArchiveSessionState.UPDATING) return
        val current = snapshot(session.workspaceRoot)
        val dirty = diffPaths(session.baseline, current)
        session.dirtyEntries = dirty
        if (session.state == ArchiveSessionState.FAILED) return
        session.state = if (dirty.isEmpty()) ArchiveSessionState.CLEAN else ArchiveSessionState.DIRTY
        session.lastError = null
    }

    private fun diffPaths(old: Map<String, ArchiveEntryStamp>, current: Map<String, ArchiveEntryStamp>): Set<String> =
        (old.keys + current.keys).filterTo(linkedSetOf()) { old[it] != current[it] }

    private fun snapshot(root: File): Map<String, ArchiveEntryStamp> {
        if (!root.isDirectory) return emptyMap()
        return root.walkTopDown()
            .onEnter { directory -> directory == root || !Files.isSymbolicLink(directory.toPath()) }
            .filter { it != root }
            .associate { file ->
                val relative = file.relativeTo(root).invariantSeparatorsPath
                val symbolic = Files.isSymbolicLink(file.toPath())
                relative to ArchiveEntryStamp(
                    directory = !symbolic && file.isDirectory,
                    size = if (!symbolic && file.isFile) file.length() else 0L,
                    modifiedAt = file.lastModified(),
                    digestHint = if (symbolic) "link:" + Files.readSymbolicLink(file.toPath()).toString() else if (file.isFile) digestHint(file) else "dir",
                    mode = runCatching { android.system.Os.lstat(file.absolutePath).st_mode and 0xFFF }.getOrNull(),
                )
            }
    }

    private fun digestHint(file: File, checkCancelled: () -> Unit = {}): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(16 * 1024)
            while (true) {
                checkCancelled()
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun MutableSession.toSnapshot() = ArchiveSessionSnapshot(
        id = id,
        archive = archive,
        workspaceRoot = workspaceRoot,
        returnDirectory = returnDirectory,
        parentId = parentId,
        state = state,
        depth = depth,
        dirtyEntries = dirtyEntries,
        lastError = lastError,
        charset = charset,
    )
}
