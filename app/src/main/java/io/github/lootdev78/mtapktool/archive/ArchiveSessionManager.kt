package io.github.lootdev78.mtapktool.archive

import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.nio.file.Files
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

enum class ArchiveSessionState { OPENING, CLEAN, DIRTY, UPDATING, FAILED, CLOSED }

data class ArchiveEntryStamp(
    val directory: Boolean,
    val size: Long,
    val modifiedAt: Long,
    val digestHint: String,
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
)

/**
 * Session layer used by the explorer. It mirrors the stateful behavior of a
 * desktop/MT-style archive browser without depending on proprietary code.
 */
class ArchiveSessionManager(private val cacheRoot: File) {
    private data class MutableSession(
        val id: String,
        val archive: File,
        val workspaceRoot: File,
        val returnDirectory: String,
        val password: String,
        val parentId: String?,
        val depth: Int,
        var baseline: Map<String, ArchiveEntryStamp>,
        var state: ArchiveSessionState,
        var dirtyEntries: Set<String> = emptySet(),
        var lastError: String? = null,
    )

    private val sessions = ConcurrentHashMap<String, MutableSession>()
    private val passwordVault = ConcurrentHashMap<String, String>()

    fun cachedPassword(archive: File): String? = passwordVault[archive.canonicalPath]

    fun open(
        archive: File,
        returnDirectory: String,
        password: String = "",
        parentId: String? = null,
        onProgress: (Int) -> Unit = {},
    ): ArchiveSessionSnapshot {
        if (!ArchiveEngine.supports(archive)) throw IOException("Unsupported archive: ${archive.name}")
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
            parentId = parentId,
            depth = (parent?.depth ?: -1) + 1,
            baseline = emptyMap(),
            state = ArchiveSessionState.OPENING,
        )
        sessions[id] = session
        try {
            ArchiveEngine.extractToDirectory(session.archive, workspace, effectivePassword, onProgress)
            session.baseline = snapshot(workspace)
            session.state = ArchiveSessionState.CLEAN
            session.lastError = null
            if (effectivePassword.isNotBlank()) passwordVault[session.archive.canonicalPath] = effectivePassword
            return session.toSnapshot()
        } catch (t: Throwable) {
            session.state = ArchiveSessionState.FAILED
            session.lastError = t.message ?: t.javaClass.simpleName
            if (effectivePassword.isNotBlank()) passwordVault.remove(session.archive.canonicalPath)
            workspace.deleteRecursively()
            sessions.remove(id)
            throw t
        }
    }

    fun get(id: String): ArchiveSessionSnapshot? = sessions[id]?.also(::refreshState)?.toSnapshot()

    fun refresh(id: String): ArchiveSessionSnapshot? = get(id)

    fun markDirty(id: String, relativePath: String? = null): ArchiveSessionSnapshot? {
        val session = sessions[id] ?: return null
        if (session.state != ArchiveSessionState.CLOSED && session.state != ArchiveSessionState.UPDATING) {
            session.state = ArchiveSessionState.DIRTY
            if (!relativePath.isNullOrBlank()) session.dirtyEntries = session.dirtyEntries + relativePath
        }
        return session.toSnapshot()
    }

    fun commit(id: String, level: ArchiveLevel = ArchiveLevel.NORMAL, onProgress: (Int) -> Unit = {}): ArchiveSessionSnapshot {
        val session = sessions[id] ?: throw IOException("Archive session no longer exists")
        refreshState(session)
        if (session.state == ArchiveSessionState.CLEAN) return session.toSnapshot()
        if (session.state == ArchiveSessionState.CLOSED) throw IOException("Archive session is closed")
        session.state = ArchiveSessionState.UPDATING
        session.lastError = null
        return try {
            onProgress(0)
            ArchiveEngine.replaceFromDirectory(session.archive, session.workspaceRoot, session.password, level)
            onProgress(100)
            session.baseline = snapshot(session.workspaceRoot)
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

    fun discard(id: String): ArchiveSessionSnapshot? {
        val session = sessions.remove(id) ?: return null
        session.state = ArchiveSessionState.CLOSED
        session.workspaceRoot.deleteRecursively()
        return session.toSnapshot()
    }

    fun closeAfterCommit(id: String): ArchiveSessionSnapshot? {
        val session = sessions.remove(id) ?: return null
        session.state = ArchiveSessionState.CLOSED
        session.workspaceRoot.deleteRecursively()
        return session.toSnapshot()
    }

    fun clearPassword(archive: File) { passwordVault.remove(archive.canonicalPath) }

    fun discardAll() {
        sessions.values.toList().forEach { session -> session.workspaceRoot.deleteRecursively() }
        sessions.clear()
        passwordVault.clear()
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
            relative to ArchiveEntryStamp(
                directory = file.isDirectory,
                size = if (file.isFile) file.length() else 0L,
                modifiedAt = file.lastModified(),
                digestHint = if (file.isFile) digestHint(file) else "dir",
            )
        }
    }

    private fun digestHint(file: File): String {
        if (!file.isFile) return ""
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(16 * 1024)
            var remaining = 64 * 1024
            while (remaining > 0) {
                val read = input.read(buffer, 0, minOf(buffer.size, remaining))
                if (read <= 0) break
                digest.update(buffer, 0, read)
                remaining -= read
            }
        }
        return digest.digest().take(8).joinToString("") { "%02x".format(it) }
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
    )
}
