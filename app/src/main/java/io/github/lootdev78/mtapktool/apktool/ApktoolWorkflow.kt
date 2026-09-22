package io.github.lootdev78.mtapktool.apktool

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.Properties
import java.util.concurrent.ConcurrentHashMap

enum class ApktoolWorkflowStage {
    QUEUED, PROVISIONING, DECODING, POST_DECODE, READY, DIRTY,
    BUILDING, POST_PROCESSING, VERIFYING, SUCCEEDED, FAILED, CANCELLED
}

data class ApktoolProjectSession(
    val projectPath: String,
    val sourceApkPath: String?,
    val state: ApktoolWorkflowStage,
    val changedFiles: Set<String>,
    val lastOutput: String?,
    val lastError: String?,
    val updatedAt: Long,
)

object ApktoolProjectSessionManager {
    private data class EntryStamp(val directory: Boolean, val size: Long, val modified: Long, val digestHint: String)
    private data class MutableSession(
        val project: File,
        var sourceApk: File?,
        var baseline: Map<String, EntryStamp>,
        var state: ApktoolWorkflowStage,
        var changed: Set<String> = emptySet(),
        var output: File? = null,
        var error: String? = null,
        var updatedAt: Long = System.currentTimeMillis(),
    )
    private data class Persisted(
        val sourceApkPath: String?,
        val state: ApktoolWorkflowStage,
        val changed: Set<String>,
        val output: String?,
        val error: String?,
        val updatedAt: Long,
    )

    private val sessions = ConcurrentHashMap<String, MutableSession>()
    @Volatile private var storageRoot: File? = null

    fun configure(appFilesDir: File) {
        val root = File(appFilesDir, "apktool-project-sessions")
        if (!root.exists()) root.mkdirs()
        storageRoot = root
    }

    fun registerDecodedProject(project: File, sourceApk: File? = null): ApktoolProjectSession {
        val canonical = project.canonicalFile
        val session = MutableSession(canonical, sourceApk?.canonicalFile, snapshot(canonical), ApktoolWorkflowStage.READY)
        sessions[canonical.path] = session
        persist(session)
        return session.toPublic()
    }

    fun open(project: File, sourceApk: File? = null): ApktoolProjectSession {
        val canonical = project.canonicalFile
        val session = sessions[canonical.path] ?: MutableSession(
            canonical,
            sourceApk?.canonicalFile,
            snapshot(canonical),
            ApktoolWorkflowStage.READY,
        ).also { sessions[canonical.path] = it }
        if (sourceApk != null) session.sourceApk = sourceApk.canonicalFile
        mergePersisted(session)
        refresh(session)
        return session.toPublic()
    }

    fun markFileChanged(file: File): ApktoolProjectSession? {
        var cursor = if (file.isDirectory) file else file.parentFile
        var depth = 0
        while (cursor != null && depth++ < 12) {
            if (File(cursor, "apktool.yml").isFile) return markDirty(cursor, file)
            cursor = cursor.parentFile
        }
        return null
    }

    fun markDirty(project: File, path: File? = null): ApktoolProjectSession {
        val canonical = project.canonicalFile
        val session = sessions[canonical.path] ?: MutableSession(canonical, null, snapshot(canonical), ApktoolWorkflowStage.READY)
            .also { sessions[canonical.path] = it }
        mergePersisted(session)
        session.state = ApktoolWorkflowStage.DIRTY
        path?.let { runCatching { it.canonicalFile.relativeTo(session.project).invariantSeparatorsPath }.getOrNull() }
            ?.takeIf { it.isNotBlank() }
            ?.let { session.changed = session.changed + it }
        session.error = null
        session.updatedAt = System.currentTimeMillis()
        persist(session)
        return session.toPublic()
    }

    fun begin(project: File, stage: ApktoolWorkflowStage): ApktoolProjectSession {
        val canonical = project.canonicalFile
        val session = sessions[canonical.path] ?: MutableSession(canonical, null, snapshot(canonical), stage)
            .also { sessions[canonical.path] = it }
        mergePersisted(session)
        session.state = stage
        session.error = null
        session.updatedAt = System.currentTimeMillis()
        persist(session)
        return session.toPublic()
    }

    fun completeBuild(project: File, output: File?): ApktoolProjectSession {
        val canonical = project.canonicalFile
        val session = sessions[canonical.path] ?: MutableSession(canonical, null, snapshot(canonical), ApktoolWorkflowStage.SUCCEEDED)
            .also { sessions[canonical.path] = it }
        mergePersisted(session)
        session.output = output?.canonicalFile
        session.baseline = snapshot(canonical)
        session.changed = emptySet()
        session.state = ApktoolWorkflowStage.SUCCEEDED
        session.error = null
        session.updatedAt = System.currentTimeMillis()
        persist(session)
        return session.toPublic()
    }

    fun fail(project: File, error: Throwable): ApktoolProjectSession {
        val canonical = project.canonicalFile
        val session = sessions[canonical.path] ?: MutableSession(canonical, null, snapshot(canonical), ApktoolWorkflowStage.FAILED)
            .also { sessions[canonical.path] = it }
        mergePersisted(session)
        session.state = ApktoolWorkflowStage.FAILED
        session.error = error.message ?: error.javaClass.simpleName
        session.updatedAt = System.currentTimeMillis()
        persist(session)
        return session.toPublic()
    }

    fun cancel(project: File): ApktoolProjectSession {
        val canonical = project.canonicalFile
        val session = sessions[canonical.path] ?: MutableSession(canonical, null, snapshot(canonical), ApktoolWorkflowStage.CANCELLED)
            .also { sessions[canonical.path] = it }
        mergePersisted(session)
        session.state = ApktoolWorkflowStage.CANCELLED
        session.updatedAt = System.currentTimeMillis()
        persist(session)
        return session.toPublic()
    }

    fun refresh(project: File): ApktoolProjectSession = open(project)

    private fun refresh(session: MutableSession) {
        if (!session.project.isDirectory) return
        val current = snapshot(session.project)
        val changed = (session.baseline.keys + current.keys).filterTo(linkedSetOf()) { session.baseline[it] != current[it] }
        session.changed = session.changed + changed
        if (session.state in setOf(ApktoolWorkflowStage.READY, ApktoolWorkflowStage.DIRTY)) {
            session.state = if (session.changed.isEmpty()) ApktoolWorkflowStage.READY else ApktoolWorkflowStage.DIRTY
        } else if (session.state == ApktoolWorkflowStage.SUCCEEDED && changed.isNotEmpty()) {
            session.state = ApktoolWorkflowStage.DIRTY
            session.changed = changed
        }
        session.updatedAt = maxOf(session.updatedAt, System.currentTimeMillis())
        persist(session)
    }

    private fun mergePersisted(session: MutableSession) {
        val persisted = readPersisted(session.project) ?: return
        if (persisted.updatedAt <= session.updatedAt) return
        session.sourceApk = persisted.sourceApkPath?.takeIf { it.isNotBlank() }?.let(::File)
        session.output = persisted.output?.takeIf { it.isNotBlank() }?.let(::File)
        session.error = persisted.error
        session.state = persisted.state
        session.changed = persisted.changed
        session.updatedAt = persisted.updatedAt
        if (persisted.state in setOf(ApktoolWorkflowStage.SUCCEEDED, ApktoolWorkflowStage.READY)) {
            session.baseline = snapshot(session.project)
            session.changed = emptySet()
        }
    }

    private fun persist(session: MutableSession) {
        val file = stateFile(session.project) ?: return
        runCatching {
            file.parentFile?.mkdirs()
            val props = Properties().apply {
                setProperty("project", session.project.absolutePath)
                setProperty("source", session.sourceApk?.absolutePath.orEmpty())
                setProperty("state", session.state.name)
                setProperty("changed", session.changed.joinToString("\u001F"))
                setProperty("output", session.output?.absolutePath.orEmpty())
                setProperty("error", session.error.orEmpty())
                setProperty("updated", session.updatedAt.toString())
            }
            val temp = File(file.parentFile, ".${file.name}.${System.nanoTime()}.tmp")
            FileOutputStream(temp).use { props.store(it, "MTApktool Apktool project state") }
            runCatching { Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE) }
                .recoverCatching { Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING) }
                .getOrThrow()
        }
    }

    private fun readPersisted(project: File): Persisted? {
        val file = stateFile(project) ?: return null
        if (!file.isFile) return null
        return runCatching {
            val props = Properties().apply { FileInputStream(file).use { input -> load(input) } }
            if (props.getProperty("project").orEmpty() != project.canonicalPath) return@runCatching null
            Persisted(
                sourceApkPath = props.getProperty("source").orEmpty().ifBlank { null },
                state = runCatching { ApktoolWorkflowStage.valueOf(props.getProperty("state")) }.getOrDefault(ApktoolWorkflowStage.READY),
                changed = props.getProperty("changed").orEmpty().split('\u001F').filter(String::isNotBlank).toSet(),
                output = props.getProperty("output").orEmpty().ifBlank { null },
                error = props.getProperty("error").orEmpty().ifBlank { null },
                updatedAt = props.getProperty("updated")?.toLongOrNull() ?: 0L,
            )
        }.getOrNull()
    }

    private fun stateFile(project: File): File? {
        val root = storageRoot ?: return null
        val digest = MessageDigest.getInstance("SHA-256").digest(project.canonicalPath.toByteArray())
            .take(12).joinToString("") { "%02x".format(it) }
        return File(root, "$digest.properties")
    }

    private fun snapshot(root: File): Map<String, EntryStamp> {
        if (!root.isDirectory) return emptyMap()
        return root.walkTopDown()
            .onEnter { directory -> directory == root || !Files.isSymbolicLink(directory.toPath()) }
            .filter { it != root }
            .associate { file ->
                file.relativeTo(root).invariantSeparatorsPath to EntryStamp(
                    file.isDirectory,
                    if (file.isFile) file.length() else 0L,
                    file.lastModified(),
                    if (file.isFile) digestHint(file) else "dir",
                )
            }
    }

    private fun digestHint(file: File): String {
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

    private fun MutableSession.toPublic() = ApktoolProjectSession(
        projectPath = project.absolutePath,
        sourceApkPath = sourceApk?.absolutePath,
        state = state,
        changedFiles = changed,
        lastOutput = output?.absolutePath,
        lastError = error,
        updatedAt = updatedAt,
    )
}
