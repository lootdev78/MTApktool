package io.github.lootdev78.mtapktool.feature.explorer.viewmodel

import io.github.lootdev78.mtapktool.feature.explorer.util.deleteTreeSafely

import android.app.Application
import android.net.Uri
import io.github.lootdev78.mtftp.FtpPaths
import io.github.lootdev78.mtapktool.tasks.ToolTaskRegistry
import io.github.lootdev78.mtapktool.tasks.ToolTaskStatus
import io.github.lootdev78.mtapktool.feature.ftp.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.currentCoroutineContext
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.lootdev78.mtapktool.core.storage.SharedStorage
import io.github.lootdev78.mtapktool.archive.ArchiveEngine
import io.github.lootdev78.mtapktool.archive.ArchiveExtractRequest
import io.github.lootdev78.mtapktool.archive.ArchiveConflictAction
import io.github.lootdev78.mtapktool.archive.ArchiveRequest
import io.github.lootdev78.mtapktool.archive.ArchiveSessionManager
import io.github.lootdev78.mtapktool.archive.ArchiveSessionSnapshot
import io.github.lootdev78.mtapktool.archive.ArchiveSessionState
import io.github.lootdev78.mtapktool.archive.ArchiveTaskInfo
import io.github.lootdev78.mtapktool.archive.ArchiveTaskKind
import io.github.lootdev78.mtapktool.archive.ArchiveTaskStatus
import io.github.lootdev78.mtapktool.archive.ArchiveTestReport
import io.github.lootdev78.mtapktool.feature.explorer.model.FileItem
import io.github.lootdev78.mtapktool.feature.explorer.saf.SafFileSystem
import io.github.lootdev78.mtapktool.feature.explorer.state.FileFilter
import io.github.lootdev78.mtapktool.feature.explorer.state.PaneState
import io.github.lootdev78.mtapktool.feature.explorer.state.SortSpec
import io.github.lootdev78.mtapktool.feature.explorer.state.RenamePreview
import io.github.lootdev78.mtapktool.feature.explorer.util.FileWorkflow
import io.github.lootdev78.mtapktool.settings.ExplorerPreferences
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.util.Stack
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

enum class ActivePane { LEFT, RIGHT }

enum class FileConflictAction { OVERWRITE, SKIP, KEEP_BOTH, CANCEL }

data class FileConflictRequest(
    val id: Long,
    val name: String,
    val source: String,
    val destination: String,
    val directory: Boolean,
)

enum class ArchiveUpdateDecision { UPDATE, DISCARD, CANCEL }

data class ArchiveUpdateRequest(
    val pane: ActivePane,
    val archiveName: String,
    val sessionState: ArchiveSessionState,
    val dirtyEntries: Set<String>,
    val nestedDepth: Int,
)

enum class ArchivePasswordPurpose { OPEN, EXTRACT }

data class ArchivePasswordRequest(
    val pane: ActivePane,
    val archive: File,
    val purpose: ArchivePasswordPurpose,
    val extractRequest: ArchiveExtractRequest? = null,
    val message: String? = null,
)

private class TransferCancelledException : IOException("Transfer cancelled")

class ExplorerViewModel(application: Application) : AndroidViewModel(application) {

    private val app: Application get() = getApplication()

    private val _leftPaneState = MutableStateFlow(PaneState())
    val leftPaneState: StateFlow<PaneState> = _leftPaneState.asStateFlow()

    private val _rightPaneState = MutableStateFlow(PaneState())
    val rightPaneState: StateFlow<PaneState> = _rightPaneState.asStateFlow()


    private val panePreferences = app.getSharedPreferences("explorer_pane_state", android.content.Context.MODE_PRIVATE)
    private val _activePane = MutableStateFlow(
        runCatching { ActivePane.valueOf(panePreferences.getString("active_pane", ActivePane.LEFT.name) ?: ActivePane.LEFT.name) }
            .getOrDefault(ActivePane.LEFT)
    )
    val activePane: StateFlow<ActivePane> = _activePane.asStateFlow()

    private val _operationMessages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val operationMessages: SharedFlow<String> = _operationMessages.asSharedFlow()

    private val _fileConflict = MutableStateFlow<FileConflictRequest?>(null)
    val fileConflict: StateFlow<FileConflictRequest?> = _fileConflict.asStateFlow()
    private val conflictId = AtomicLong(0L)
    private val conflictMutex = Mutex()
    private var pendingConflict: CompletableDeferred<FileConflictAction>? = null
    @Volatile private var batchConflictAction: FileConflictAction? = null

    private val network = NetworkFileOperations(app)
    data class RemoteEdit(val item: FileItem, val file: File, val remoteHash: String, val localHash: String)
    private val remoteEdits = ConcurrentHashMap<String, RemoteEdit>()
    private val documentParentByFile = ConcurrentHashMap<String, String>()
    private val _remoteEditRequest = MutableStateFlow<RemoteEdit?>(null)
    val remoteEditRequest = _remoteEditRequest.asStateFlow()
    private val loadGeneration = ActivePane.entries.associateWith { AtomicLong(0) }
    private val searchJobs = ConcurrentHashMap<ActivePane, Job>()
    private val searchParents = ConcurrentHashMap<ActivePane, Map<String, String>>()
    private val searchSpecs = ConcurrentHashMap<ActivePane, FileWorkflow.SearchSpec>()
    private val mutationRunning = java.util.concurrent.atomic.AtomicBoolean(false)
    private var transferJob: Job? = null
    private val _fileTransfer = MutableStateFlow(FileTransferState())
    val fileTransfer: StateFlow<FileTransferState> = _fileTransfer.asStateFlow()

    fun connectFtp(pane: ActivePane, profile: FtpProfile, password: String, result: (String?) -> Unit) {
        if (currentArchiveSession(pane) != null) { result("Archiv zuerst schließen oder aktualisieren"); return }
        if (_fileTransfer.value.running) { result("Dateiübertragung läuft"); return }
        viewModelScope.launch {
            val outcome = runCatching { ToolTaskRegistry.run("FTP verbinden", "${profile.host}:${profile.port}") { withContext(Dispatchers.IO) { network.connect(profile, password) } } }
            outcome.onSuccess { path ->
                val previous = paneState(pane).currentPath
                if (FtpLocation.isRemote(previous) && paneState(if (pane == ActivePane.LEFT) ActivePane.RIGHT else ActivePane.LEFT).currentPath.let {
                        !FtpLocation.isRemote(it) || FtpLocation.session(it) != FtpLocation.session(previous)
                    }) withContext(Dispatchers.IO) { network.disconnect(previous) }
                setActive(pane)
                loadDirectory(pane, path)
                result(null)
            }.onFailure { result(it.message ?: "FTP-Verbindung fehlgeschlagen") }
        }
    }

    fun disconnectFtp(pane: ActivePane) {
        if (_fileTransfer.value.running) { _operationMessages.tryEmit("Übertragung zuerst beenden"); return }
        val path = paneState(pane).currentPath
        if (!FtpLocation.isRemote(path)) return
        val other = paneState(if (pane == ActivePane.LEFT) ActivePane.RIGHT else ActivePane.LEFT).currentPath
        loadDirectory(pane, SharedStorage.primaryRoot().absolutePath)
        if (!FtpLocation.isRemote(other) || FtpLocation.session(other) != FtpLocation.session(path)) {
            viewModelScope.launch(Dispatchers.IO) { network.disconnect(path) }
        }
    }

    suspend fun materializeFtpItem(item: FileItem): File = ToolTaskRegistry.run("FTP herunterladen", item.name, network::abortAll) { id ->
        withContext(Dispatchers.IO) {
            val job = currentCoroutineContext()[Job]
            val downloaded = network.materialize(item) { bytes -> job?.ensureActive(); ToolTaskRegistry.progress(id, message = "${item.name}: $bytes Bytes") }
            val hash = network.sha256(downloaded)
            reusableEdit(item, hash)?.let { existing ->
                downloaded.parentFile?.deleteTreeSafely()
                remoteEdits[existing.file.canonicalPath] = existing.copy(item = item)
                return@withContext existing.file
            }
            val persistentDirectory = File(app.filesDir, "ftp-editor/${java.util.UUID.randomUUID()}")
            if (!persistentDirectory.mkdirs()) throw IOException("Lokale FTP-Arbeitskopie konnte nicht erstellt werden")
            val file = File(persistentDirectory, item.name)
            java.nio.file.Files.move(downloaded.toPath(), file.toPath())
            downloaded.parentFile?.deleteTreeSafely()
            remoteEdits[file.canonicalPath] = RemoteEdit(item, file, hash, hash)
            file
        }
    }

    private fun reusableEdit(item: FileItem, sourceHash: String): RemoteEdit? {
        val activeFiles = archiveSessionIds.values.mapNotNull(archiveSessionManager::peek).map { it.archive.canonicalPath }.toSet()
        return remoteEdits.values.sortedByDescending { it.file.canonicalPath in activeFiles }.firstOrNull { edit ->
            edit.file.isFile && network.sameSource(edit.item, item) && edit.remoteHash == sourceHash &&
                (edit.file.canonicalPath in activeFiles || runCatching { network.sha256(edit.file) == sourceHash }.getOrDefault(false))
        }
    }
    suspend fun materializeForArchive(item: FileItem): File {
        if (!item.isFtp && !item.isSaf) return item.file
        if (item.isFtp && !item.isDirectory) return materializeFtpItem(item)
        return ToolTaskRegistry.run("Dateien für Archiv vorbereiten", item.name, network::abortAll) { taskId ->
            withContext(Dispatchers.IO) {
                val root = File(app.cacheDir, "archive-source-${java.util.UUID.randomUUID()}")
                if (!root.mkdirs()) throw IOException("Arbeitsordner konnte nicht erstellt werden")
                val job = currentCoroutineContext()[Job]
                try {
                    network.copy(item, root.absolutePath, { _, _ -> FileConflictAction.CANCEL }, { n -> ToolTaskRegistry.progress(taskId, message = "${item.name}: $n Bytes") }, { job?.ensureActive() })
                    File(root, item.name)
                } catch (error: Throwable) { root.deleteTreeSafely(); throw error }
            }
        }
    }

    suspend fun materializeDocumentItem(item: FileItem, pane: ActivePane): File = ToolTaskRegistry.run("Dokument vorbereiten", item.name) { task ->
        withContext(Dispatchers.IO) {
            val job = currentCoroutineContext()[Job]
            val state = paneState(pane)
            val parent = searchParents[pane]?.get(item.path) ?: state.currentPath.takeIf { SafFileSystem.isSafPath(it) && state.items.any { row -> row.path == item.path } }
                ?: runCatching { io.github.lootdev78.mtapktool.feature.explorer.util.ExternalUriLocator.locate(app, Uri.parse(item.path))?.parentDocumentUri }.getOrNull()
            val directory = File(app.filesDir, "document-editor/${java.util.UUID.randomUUID()}")
            if (!directory.mkdirs()) throw IOException("Lokale Dokument-Arbeitskopie konnte nicht erstellt werden")
            val output = File(directory, FtpPaths.name(item.name))
            try {
                app.contentResolver.openInputStream(Uri.parse(item.path))?.use { input -> output.outputStream().use { out ->
                    val buffer = ByteArray(128 * 1024); var total = 0L
                    while (true) { job?.ensureActive(); val n = input.read(buffer); if (n < 0) break; if (n > 0) { out.write(buffer, 0, n); total += n; ToolTaskRegistry.progress(task, message = "${item.name}: $total Bytes") } }
                } } ?: throw IOException("Dokument nicht lesbar")
                val hash = network.sha256(output)
                reusableEdit(item, hash)?.let { existing ->
                    directory.deleteTreeSafely()
                    remoteEdits[existing.file.canonicalPath] = existing.copy(item = item)
                    if (parent != null) documentParentByFile[existing.file.canonicalPath] = parent
                    return@withContext existing.file
                }
                remoteEdits[output.canonicalPath] = RemoteEdit(item, output, hash, hash)
                if (parent != null) documentParentByFile[output.canonicalPath] = parent
                output
            } catch (error: Throwable) { directory.deleteTreeSafely(); throw error }
        }
    }

    fun resolveRemoteEdit(upload: Boolean) {
        val edit = _remoteEditRequest.value ?: return
        _remoteEditRequest.value = null
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val (hash, updatedItem) = if (upload) ToolTaskRegistry.run(if (edit.item.isSaf) "Dokumentänderung zurückschreiben" else "FTP-Änderung hochladen", edit.item.name,
                    { if (edit.item.isFtp) network.abort(edit.item.path) }) {
                    val snapshot = File.createTempFile("mt-edit-upload-", ".tmp", app.cacheDir)
                    try {
                        edit.file.inputStream().use { input -> snapshot.outputStream().use { out ->
                            val buffer = ByteArray(128 * 1024)
                            while (true) { this@launch.ensureActive(); val n = input.read(buffer); if (n < 0) break; if (n > 0) out.write(buffer, 0, n) }
                        } }
                        val hash = network.sha256(snapshot)
                        val item = if (edit.item.isSaf) {
                            val parent = documentParentByFile[edit.file.canonicalPath] ?: throw IOException("Originalordner ist nicht freigegeben; Ordnerzugriff über Speicher hinzufügen erlauben. Die lokale Änderung bleibt erhalten.")
                            val uri = network.uploadDocumentEdited(edit.item, snapshot, parent, edit.remoteHash) { this@launch.ensureActive() }
                            edit.item.copy(safUri = uri.toString(), modifiedOverride = System.currentTimeMillis(), sizeOverride = snapshot.length())
                        } else { network.uploadEdited(edit.item, snapshot, edit.remoteHash) { this@launch.ensureActive() }; edit.item }
                        hash to item
                    } finally { snapshot.delete() }
                } else network.sha256(edit.file) to edit.item
                remoteEdits[edit.file.canonicalPath] = edit.copy(item = updatedItem, remoteHash = if (upload) hash else edit.remoteHash, localHash = hash)
                val parent = if (edit.item.isSaf) documentParentByFile[edit.file.canonicalPath] else FtpLocation.parent(edit.item.path)
                if (upload && parent != null) ActivePane.entries.filter { paneState(it).currentPath == parent }.forEach { highlightDocument(it, parent, edit.item.name) }
                refreshMountedArchiveStates()
                continueExit()
            } catch (error: Exception) {
                _operationMessages.tryEmit((error.message ?: "Änderung konnte nicht übertragen werden") + error.suppressed.joinToString("") { "\n${it.message}" })
                _remoteEditRequest.value = edit
            }
        }
    }

    fun cancelFileTransfer() {
        transferJob?.cancel()
        // Socket disconnect interrupts a stalled data read as well as cancelling the coroutine.
        network.abortAll()
    }
    suspend fun ftpPermissions(path: String): Int? = withContext(Dispatchers.IO) { network.connection(path).client.permissions(FtpLocation.remotePath(path)) }
    suspend fun setFtpPermissions(path: String, mode: Int) = withContext(Dispatchers.IO) { network.connection(path).client.chmod(FtpLocation.remotePath(path), mode) }

    fun selectSameType(pane: ActivePane) {
        updatePaneState(pane) { state ->
            val types = state.items.filter { it.path in state.selectedPaths }.map { if (it.isDirectory) "<folder>" else it.extensionName }.toSet()
            state.copy(selectedPaths = state.filteredItems.filter { (if (it.isDirectory) "<folder>" else it.extensionName) in types }.map { it.path }.toSet())
        }
    }

    // Separate Back/Forward stacks for dual pane navigation history
    private val leftBackStack = Stack<String>()
    private val leftForwardStack = Stack<String>()

    private val rightBackStack = Stack<String>()
    private val rightForwardStack = Stack<String>()

    private var leftDefaultSort = SortSpec()
    private var rightDefaultSort = SortSpec()
    private val folderSortOverrides = mutableMapOf<String, SortSpec>()
    private var manualHiddenPaths: Set<String> = emptySet()
    private val safRootByPane = mutableMapOf<ActivePane, String>()
    private val safDisplayByUri = mutableMapOf<String, String>()

    private val archiveSessionManager = ArchiveSessionManager(File(app.cacheDir, "mtapktool-archive-workspaces"))
    private val archiveSessionIds = ConcurrentHashMap<ActivePane, String>()
    private data class PendingArchiveOpen(val archive: File, val password: String)
    private val pendingArchiveOpen = ConcurrentHashMap<ActivePane, PendingArchiveOpen>()
    private val pendingReveals = ConcurrentHashMap<ActivePane, Pair<String, String>>()
    @Volatile private var exitAction: (() -> Unit)? = null

    private val _archiveUpdateRequest = MutableStateFlow<ArchiveUpdateRequest?>(null)
    val archiveUpdateRequest: StateFlow<ArchiveUpdateRequest?> = _archiveUpdateRequest.asStateFlow()
    private val _archivePasswordRequest = MutableStateFlow<ArchivePasswordRequest?>(null)
    val archivePasswordRequest: StateFlow<ArchivePasswordRequest?> = _archivePasswordRequest.asStateFlow()
    private val _archiveTestReport = MutableStateFlow<ArchiveTestReport?>(null)
    val archiveTestReport: StateFlow<ArchiveTestReport?> = _archiveTestReport.asStateFlow()

    private val archiveTaskId = AtomicLong(0L)
    private val archiveTaskJobs = ConcurrentHashMap<Long, Job>()
    private val _archiveTasks = MutableStateFlow<List<ArchiveTaskInfo>>(emptyList())
    val archiveTasks: StateFlow<List<ArchiveTaskInfo>> = _archiveTasks.asStateFlow()

    private fun currentArchiveSession(pane: ActivePane): ArchiveSessionSnapshot? =
        archiveSessionIds[pane]?.let(archiveSessionManager::peek)

    private fun archiveDisplayName(session: ArchiveSessionSnapshot): String {
        remoteEdits[session.archive.canonicalPath]?.let { edit ->
            if (edit.item.isFtp) return runCatching { network.display(edit.item.path) }.getOrDefault(edit.item.name)
            if (edit.item.isSaf) {
                val parent = documentParentByFile[edit.file.canonicalPath]
                return (parent?.let { safDisplayByUri[it] } ?: "Dokumente") + "/" + edit.item.name
            }
        }
        val parent = session.parentId?.let(archiveSessionManager::peek)
        if (parent != null) return archiveDisplayName(parent) + "!/" + session.archive.relativeTo(parent.workspaceRoot).invariantSeparatorsPath
        return session.archive.absolutePath
    }

    private fun usedByOtherPane(pane: ActivePane, sessionId: String): Boolean =
        ActivePane.entries.filter { it != pane }.any { other ->
            var current = currentArchiveSession(other)
            while (current != null) {
                if (current.id == sessionId) return@any true
                current = current.parentId?.let(archiveSessionManager::peek)
            }
            false
        }

    private fun publishArchiveStates() {
        ActivePane.entries.forEach { pane ->
            val session = currentArchiveSession(pane)
            updatePaneState(pane) { it.copy(archiveStatus = session?.state, archiveChanges = session?.dirtyEntries?.size ?: 0, archiveCharset = session?.charset.orEmpty()) }
        }
    }

    private fun beginArchiveTask(kind: ArchiveTaskKind, title: String, detail: String): Long {
        val id = archiveTaskId.incrementAndGet()
        _archiveTasks.update { listOf(ArchiveTaskInfo(id, kind, title, detail, ArchiveTaskStatus.RUNNING, 0)) + it }
        return id
    }

    private fun updateArchiveTask(id: Long, progress: Int? = null, message: String? = null) {
        _archiveTasks.update { tasks -> tasks.map { task ->
            if (task.id == id) task.copy(progress = progress ?: task.progress, message = message ?: task.message) else task
        } }
    }

    private fun finishArchiveTask(id: Long, success: Boolean, message: String = "") {
        _archiveTasks.update { tasks -> tasks.map { task ->
            if (task.id == id) task.copy(
                status = if (success) ArchiveTaskStatus.SUCCEEDED else ArchiveTaskStatus.FAILED,
                progress = if (success) 100 else task.progress,
                message = message,
            ) else task
        } }
        archiveTaskJobs.remove(id)
        viewModelScope.launch {
            delay(1500)
            _archiveTasks.update { tasks -> tasks.filterNot { it.id == id && it.isTerminal } }
        }
    }

    fun cancelArchiveTask(id: Long) {
        archiveTaskJobs.remove(id)?.cancel()
        pendingConflict?.complete(FileConflictAction.CANCEL)
        pendingConflict?.complete(FileConflictAction.CANCEL)
        _archiveTasks.update { tasks -> tasks.map { task ->
            if (task.id == id) task.copy(status = ArchiveTaskStatus.CANCELLED, message = "Cancelled") else task
        } }
        viewModelScope.launch {
            delay(1000)
            _archiveTasks.update { tasks -> tasks.filterNot { it.id == id && it.isTerminal } }
        }
    }

    fun cancelAllArchiveTasks() = archiveTaskJobs.keys.toList().forEach(::cancelArchiveTask)


    init {
        ExplorerPreferences.init(app)
        manualHiddenPaths = panePreferences.getStringSet("manually_hidden_files", emptySet()).orEmpty().toSet()
        val showHidden = panePreferences.getBoolean("show_system_hidden", true)
        val showManual = panePreferences.getBoolean("show_manual_hidden", true)
        ActivePane.entries.forEach { pane -> updatePaneState(pane) { it.copy(showSystemHidden = showHidden, showManuallyHidden = showManual, manuallyHiddenPaths = manualHiddenPaths) } }
        fun readSort(key: String): SortSpec = runCatching {
            val json = org.json.JSONObject(panePreferences.getString(key, "{}") ?: "{}")
            SortSpec(io.github.lootdev78.mtapktool.feature.explorer.state.SortField.valueOf(json.optString("field", "NAME")), json.optBoolean("descending", false))
        }.getOrDefault(SortSpec())
        leftDefaultSort = readSort("sort_LEFT"); rightDefaultSort = readSort("sort_RIGHT")
        runCatching {
            val sorts = org.json.JSONObject(panePreferences.getString("folder_sorts", "{}") ?: "{}")
            sorts.keys().forEach { key -> val entry = sorts.getJSONObject(key); folderSortOverrides[key] = SortSpec(io.github.lootdev78.mtapktool.feature.explorer.state.SortField.valueOf(entry.getString("field")), entry.optBoolean("descending")) }
        }
        val prefs = ExplorerPreferences.current(app)
        val rootPath = SharedStorage.primaryRoot().absolutePath
        val leftStart = if (prefs.startupLeft == "last") panePreferences.getString("last_left_path", rootPath) ?: rootPath else panePreferences.getString("home_LEFT", rootPath) ?: rootPath
        val rightStart = if (prefs.startupRight == "last") panePreferences.getString("last_right_path", rootPath) ?: rootPath else panePreferences.getString("home_RIGHT", rootPath) ?: rootPath
        cleanRecycleBinIfNeeded(prefs.customWorkspace, prefs.autoCleanRecycleBinDays)
        loadDirectory(ActivePane.LEFT, leftStart, isHistoryAction = false)
        loadDirectory(ActivePane.RIGHT, rightStart, isHistoryAction = false)
    }

    fun setActive(pane: ActivePane) {
        _activePane.value = pane
        panePreferences.edit().putString("active_pane", pane.name).apply()
    }

    fun resolveFileConflict(action: FileConflictAction, applyToAll: Boolean) {
        if (applyToAll && action != FileConflictAction.CANCEL) batchConflictAction = action
        pendingConflict?.complete(action)
    }

    private suspend fun askConflict(item: FileItem, destination: String): FileConflictAction =
        askConflict(item.name, item.path, destination, item.isDirectory)

    private suspend fun askConflict(name: String, source: String, destination: String, directory: Boolean): FileConflictAction {
        batchConflictAction?.let { return it }
        return conflictMutex.withLock {
            batchConflictAction?.let { return@withLock it }
            val request = FileConflictRequest(
                id = conflictId.incrementAndGet(),
                name = name,
                source = source,
                destination = destination,
                directory = directory,
            )
            val deferred = CompletableDeferred<FileConflictAction>()
            pendingConflict = deferred
            _fileConflict.value = request
            try {
                deferred.await()
            } finally {
                if (pendingConflict === deferred) pendingConflict = null
                if (_fileConflict.value?.id == request.id) _fileConflict.value = null
            }
        }
    }

    private fun beginTransferBatch() {
        batchConflictAction = null
    }

    fun setPaneBusy(pane: ActivePane, busy: Boolean, label: String? = null, progress: Int? = null) {
        updatePaneState(pane) {
            it.copy(
                isLoading = busy,
                loadingLabel = if (busy) label else null,
                loadingProgress = if (busy) progress?.coerceIn(0, 100) else null,
            )
        }
    }

    fun openCustomLocation(pane: ActivePane, rootDocumentUri: String, displayName: String) {
        safRootByPane[pane] = rootDocumentUri
        safDisplayByUri[rootDocumentUri] = displayName
        val back = if (pane == ActivePane.LEFT) leftBackStack else rightBackStack
        val forward = if (pane == ActivePane.LEFT) leftForwardStack else rightForwardStack
        back.clear()
        forward.clear()
        loadDirectory(pane, rootDocumentUri, isHistoryAction = true)
    }

    fun loadDirectory(pane: ActivePane, path: String, isHistoryAction: Boolean = false, highlightName: String? = null) {
        searchJobs.remove(pane)?.cancel()
        searchSpecs.remove(pane)
        val generation = loadGeneration.getValue(pane).incrementAndGet()
        val previousState = paneState(pane)
        val currentPath = previousState.currentPath

        if (!isHistoryAction && currentPath.isNotEmpty() && currentPath != path) {
            if (pane == ActivePane.LEFT) {
                leftBackStack.push(currentPath)
                leftForwardStack.clear()
            } else {
                rightBackStack.push(currentPath)
                rightForwardStack.clear()
            }
        }

        viewModelScope.launch {
            val isRemote = FtpLocation.isRemote(path)
            val isSaf = SafFileSystem.isSafPath(path)
            val displayOverride = if (isRemote) runCatching { network.display(path) }.getOrDefault("FTP") else if (isSaf) {
                safDisplayByUri[path] ?: withContext(Dispatchers.IO) {
                    val name = SafFileSystem.documentName(app, Uri.parse(path)) ?: "Storage"
                    val parentDisplay = safDisplayByUri[previousState.currentPath]
                    val display = if (!isHistoryAction && parentDisplay != null && previousState.currentPath != path) "$parentDisplay/$name" else name
                    safDisplayByUri[path] = display
                    display
                }
            } else null

            updatePaneState(pane) { it.copy(isLoading = true, currentPath = path, displayPathOverride = displayOverride, searchResultsLabel = null,
                searchQuery = if (!highlightName.isNullOrEmpty()) "" else it.searchQuery,
                filter = if (!highlightName.isNullOrEmpty()) FileFilter.ALL else it.filter,
                highlightedItemPath = if (currentPath == path && highlightName == null) it.highlightedItemPath else null,
                highlightedItemName = highlightName ?: if (currentPath == path) it.highlightedItemName else null) }

            val result = withContext(Dispatchers.IO) {
                runCatching {
                    if (isRemote) network.list(path)
                    else if (isSaf) SafFileSystem.list(app, Uri.parse(path))
                    else {
                        val dir = File(path)
                        if (dir.exists() && dir.isDirectory) dir.listFiles()?.map { FileItem(file = it) } ?: emptyList()
                        else emptyList()
                    }
                }
            }

            if (loadGeneration.getValue(pane).get() != generation) return@launch
            if (result.isFailure) {
                updatePaneState(pane) { it.copy(isLoading = false, loadingProgress = null, loadingLabel = null, items = emptyList(), selectedPaths = emptySet()) }
                _operationMessages.tryEmit("Storage access failed: ${result.exceptionOrNull()?.message ?: "unknown error"}")
                return@launch
            }

            val files = result.getOrThrow()
            val sameDirectory = previousState.searchResultsLabel == null && previousState.currentPath == path && previousState.items.isNotEmpty()
            val oldModified = previousState.items.associate { it.path to Triple(it.modifiedAt, it.fileSize, it.unixMode) }
            val changedPaths = if (sameDirectory) {
                files.asSequence()
                    .filter { item -> oldModified[item.path]?.let { it != Triple(item.modifiedAt, item.fileSize, item.unixMode) } ?: true }
                    .map { it.path }
                    .toSet()
            } else emptySet()
            val revealedPaths = files.filter { it.name == highlightName }.map { it.path }.toSet()
            val emphasizedPaths = changedPaths + revealedPaths
            val highlighted = files.firstOrNull { it.name == highlightName } ?: files.filter { it.path in changedPaths }.maxByOrNull { it.modifiedAt }
            val sort = folderSortOverrides[sortKey(pane, path)] ?: defaultSort(pane)

            updatePaneState(pane) { state ->
                state.copy(
                    items = files,
                    isLoading = false,
                    loadingProgress = null,
                    loadingLabel = null,
                    selectedPaths = if (previousState.currentPath == path) state.selectedPaths.intersect(files.map { it.path }.toSet()) else emptySet(),
                    manuallyHiddenPaths = manualHiddenPaths,
                    sortSpec = sort,
                    recentlyChangedPaths = (if (sameDirectory) state.recentlyChangedPaths else emptySet()) + emphasizedPaths,
                    highlightedItemName = highlightName ?: files.filter { it.path in changedPaths }.maxByOrNull { it.modifiedAt }?.name ?: state.highlightedItemName,
                    highlightedItemPath = highlighted?.path ?: state.highlightedItemPath,
                    highlightEvent = state.highlightEvent + if (emphasizedPaths.isNotEmpty()) 1L else 0L,
                    displayPathOverride = displayOverride,
                )
            }

            if (!isRemote && !paneState(pane).isArchiveView) panePreferences.edit()
                .putString(if (pane == ActivePane.LEFT) "last_left_path" else "last_right_path", path)
                .apply()

            if (emphasizedPaths.isNotEmpty()) {
                val event = paneState(pane).highlightEvent
                viewModelScope.launch {
                    delay(RECENT_HIGHLIGHT_MS)
                    updatePaneState(pane) { state ->
                        if (state.currentPath != path || state.highlightEvent != event) state else state.copy(
                            recentlyChangedPaths = state.recentlyChangedPaths - emphasizedPaths,
                            highlightedItemName = if (state.items.any { it.path in emphasizedPaths && it.name == state.highlightedItemName }) null else state.highlightedItemName,
                            highlightedItemPath = if (state.highlightedItemPath in emphasizedPaths) null else state.highlightedItemPath,
                        )
                    }
                }
            }
        }
    }

    // --- Navigation History Controls ---
    fun navigateHistoryBack(pane: ActivePane) {
        if (currentArchiveSession(pane) != null) {
            navigateUp(pane)
            return
        }
        val backStack = if (pane == ActivePane.LEFT) leftBackStack else rightBackStack
        val forwardStack = if (pane == ActivePane.LEFT) leftForwardStack else rightForwardStack
        val currentPath = if (pane == ActivePane.LEFT) _leftPaneState.value.currentPath else _rightPaneState.value.currentPath

        if (backStack.isNotEmpty()) {
            forwardStack.push(currentPath)
            val previousPath = backStack.pop()
            loadDirectory(pane, previousPath, isHistoryAction = true)
        }
    }

    fun navigateHistoryForward(pane: ActivePane) {
        if (currentArchiveSession(pane) != null) return
        val backStack = if (pane == ActivePane.LEFT) leftBackStack else rightBackStack
        val forwardStack = if (pane == ActivePane.LEFT) leftForwardStack else rightForwardStack
        val currentPath = if (pane == ActivePane.LEFT) _leftPaneState.value.currentPath else _rightPaneState.value.currentPath

        if (forwardStack.isNotEmpty()) {
            backStack.push(currentPath)
            val nextPath = forwardStack.pop()
            loadDirectory(pane, nextPath, isHistoryAction = true)
        }
    }

    fun toggleSelection(
        pane: ActivePane,
        itemPath: String,
        isSwipe: Boolean
    ) {
        updatePaneState(pane) { state ->

            val selected = state.selectedPaths.toMutableSet()

            if (selected.size == 1 && isSwipe) {
                val firstSelected = selected.first()

                val visibleItems = state.filteredItems
                val startIndex = visibleItems.indexOfFirst {
                    it.path == firstSelected
                }

                val endIndex = visibleItems.indexOfFirst {
                    it.path == itemPath
                }

                if (startIndex != -1 && endIndex != -1) {
                    val start = minOf(startIndex, endIndex)
                    val end = maxOf(startIndex, endIndex)

                    for (i in start..end) {
                        selected.add(visibleItems[i].path)
                    }

                    return@updatePaneState state.copy(
                        selectedPaths = selected
                    )
                }
            }

            // Normal toggle
            if (selected.contains(itemPath)) {
                selected.remove(itemPath)
            } else {
                selected.add(itemPath)
            }

            state.copy(
                selectedPaths = selected
            )
        }
    }

    fun canNavigateUp(pane: ActivePane): Boolean {
        if (paneState(pane).searchResultsLabel != null) return true
        val current = paneState(pane).currentPath
        if (FtpLocation.isRemote(current)) return FtpLocation.remotePath(current) != "/"
        if (SafFileSystem.isSafPath(current)) return current != safRootByPane[pane]
        if (currentArchiveSession(pane) != null) return true
        val rootPath = SharedStorage.primaryRoot().absolutePath
        return current != rootPath && current != "/" && File(current).parent != null
    }

    fun navigateUp(pane: ActivePane) {
        if (paneState(pane).searchResultsLabel != null) { clearAdvancedSearch(pane); return }
        val current = paneState(pane).currentPath
        if (FtpLocation.isRemote(current)) {
            if (FtpLocation.remotePath(current) != "/") loadDirectory(pane, FtpLocation.parent(current))
            return
        }
        if (SafFileSystem.isSafPath(current)) {
            if (current == safRootByPane[pane]) return
            val backStack = if (pane == ActivePane.LEFT) leftBackStack else rightBackStack
            if (backStack.isNotEmpty()) {
                val parent = backStack.pop()
                val forwardStack = if (pane == ActivePane.LEFT) leftForwardStack else rightForwardStack
                forwardStack.push(current)
                loadDirectory(pane, parent, isHistoryAction = true)
            }
            return
        }

        currentArchiveSession(pane)?.let { session ->
            val currentFile = File(current)
            val root = session.workspaceRoot
            if (sameFile(currentFile, root)) {
                requestCloseArchive(pane)
                return
            }
            val parent = currentFile.parentFile
            if (parent != null && isInside(parent, root)) {
                loadDirectory(pane, parent.absolutePath)
                return
            }
            requestCloseArchive(pane)
            return
        }

        val rootPath = SharedStorage.primaryRoot().absolutePath
        if (current == rootPath || current == "/") return
        File(current).parent?.let { loadDirectory(pane, it) }
    }

    fun refreshDirectory(pane: ActivePane) {
        if (paneState(pane).searchResultsLabel != null) {
            searchSpecs[pane]?.let { advancedSearch(pane, it); return }
        }
        val currentPath = if (pane == ActivePane.LEFT) _leftPaneState.value.currentPath else _rightPaneState.value.currentPath
        loadDirectory(pane, currentPath, isHistoryAction = true)
    }

    private inline fun updatePaneState(pane: ActivePane, update: (PaneState) -> PaneState) {
        if (pane == ActivePane.LEFT) {
            _leftPaneState.update(update)
        } else {
            _rightPaneState.update(update)
        }
    }

    fun selectAll(pane: ActivePane) {
        updatePaneState(pane) { state ->
            val allPaths = state.filteredItems.map { it.path }.toSet()
            state.copy(selectedPaths = allPaths)
        }
    }

    fun ensureSelected(pane: ActivePane, itemPath: String) {
        updatePaneState(pane) { state ->
            if (itemPath in state.selectedPaths) state
            else state.copy(selectedPaths = state.selectedPaths + itemPath)
        }
    }



    fun invertSelection(pane: ActivePane) {
        updatePaneState(pane) { state ->
            val currentSelected = state.selectedPaths
            val inverted = state.filteredItems
                .map { it.path }
                .filter { !currentSelected.contains(it) }
                .toSet()
            state.copy(selectedPaths = inverted)
        }
    }

    fun cancelSelection(pane: ActivePane){
        updatePaneState(pane) { state ->
            state.copy(selectedPaths = emptySet())
        }

    }

    fun syncOppositePane() {
        val sourcePane = activePane.value
        val targetPane = if (sourcePane == ActivePane.LEFT) ActivePane.RIGHT else ActivePane.LEFT
        currentArchiveSession(sourcePane)?.let { session ->
            openArchive(targetPane, session.archive)
            return
        }
        val sourcePath = paneState(sourcePane).currentPath
        if (SafFileSystem.isSafPath(sourcePath)) {
            safRootByPane[sourcePane]?.let { safRootByPane[targetPane] = it }
            safDisplayByUri[sourcePath]?.let { safDisplayByUri[sourcePath] = it }
        } else {
            safRootByPane.remove(targetPane)
        }
        loadDirectory(pane = targetPane, path = sourcePath)
    }

    fun copySelectedToOppositePane(fromPane: ActivePane) = transferSelected(fromPane, false)
    fun moveSelectedToOppositePane(fromPane: ActivePane) = transferSelected(fromPane, true)

    fun swapPanes() {
        if (paneState(ActivePane.LEFT).isLoading || paneState(ActivePane.RIGHT).isLoading || _fileTransfer.value.running || mutationRunning.get()) {
            _operationMessages.tryEmit("Laufende Panel-Aktion zuerst beenden"); return
        }
        ActivePane.entries.forEach { loadGeneration.getValue(it).incrementAndGet() }
        val left = _leftPaneState.value; val right = _rightPaneState.value
        _leftPaneState.value = right; _rightPaneState.value = left
        fun <T> swapMap(map: MutableMap<ActivePane, T>) {
            val a = map.remove(ActivePane.LEFT); val b = map.remove(ActivePane.RIGHT)
            if (a != null) map[ActivePane.RIGHT] = a
            if (b != null) map[ActivePane.LEFT] = b
        }
        swapMap(archiveSessionIds); swapMap(safRootByPane); swapMap(searchParents); swapMap(searchSpecs)
        fun swapHistory(a: Stack<String>, b: Stack<String>) {
            val old = a.toList(); a.clear(); a.addAll(b); b.clear(); b.addAll(old)
        }
        swapHistory(leftBackStack, rightBackStack); swapHistory(leftForwardStack, rightForwardStack)
        val oldSort = leftDefaultSort; leftDefaultSort = rightDefaultSort; rightDefaultSort = oldSort
        val overrides = folderSortOverrides.toMap(); folderSortOverrides.clear()
        overrides.forEach { (key, value) -> folderSortOverrides[if (key.startsWith("LEFT:")) "RIGHT:" + key.removePrefix("LEFT:") else "LEFT:" + key.removePrefix("RIGHT:")] = value }
        saveSortPreferences()
        ActivePane.entries.forEach { pane ->
            val state = paneState(pane)
            if (!state.isFtpView && !state.isArchiveView) panePreferences.edit().putString(if (pane == ActivePane.LEFT) "last_left_path" else "last_right_path", state.currentPath).apply()
        }
    }

    fun setAsHome(pane: ActivePane) {
        val state = paneState(pane)
        if (state.isArchiveView || state.isFtpView) { _operationMessages.tryEmit("Für Archive keinen temporären Home-Pfad speichern; FTP-Startordner im Verbindungsprofil setzen"); return }
        panePreferences.edit().putString("home_${pane.name}", state.currentPath).apply()
        _operationMessages.tryEmit("Home-Ordner für ${if (pane == ActivePane.LEFT) "links" else "rechts"} gespeichert")
    }

    fun navigateHome(pane: ActivePane) {
        val home = panePreferences.getString("home_${pane.name}", SharedStorage.primaryRoot().absolutePath) ?: SharedStorage.primaryRoot().absolutePath
        if (SafFileSystem.isSafPath(home)) {
            val uri = Uri.parse(home)
            if (android.provider.DocumentsContract.isTreeUri(uri)) safRootByPane[pane] = android.provider.DocumentsContract.buildDocumentUriUsingTree(uri, android.provider.DocumentsContract.getTreeDocumentId(uri)).toString()
        }
        highlightDocument(pane, home, "")
    }

    private fun transferSelected(fromPane: ActivePane, move: Boolean) {
        if (searchJobs.values.any { it.isActive }) { _operationMessages.tryEmit("Suche zuerst beenden"); return }
        if (mutationRunning.get()) { _operationMessages.tryEmit("Umbenennung läuft"); return }
        if (_fileTransfer.value.running) { _operationMessages.tryEmit("Eine Dateiübertragung läuft bereits"); return }
        val sourceState = paneState(fromPane)
        val targetPane = if (fromPane == ActivePane.LEFT) ActivePane.RIGHT else ActivePane.LEFT
        val targetPath = paneState(targetPane).currentPath
        val selected = sourceState.items.filter { it.path in sourceState.selectedPaths }
        if (selected.isEmpty()) return
        if (sourceState.currentPath == targetPath) { _operationMessages.tryEmit("Quelle und Ziel sind identisch"); return }
        beginTransferBatch()
        _fileTransfer.value = FileTransferState(running = true, label = if (move) "Verschieben" else "Kopieren", total = selected.size)
        transferJob = viewModelScope.launch(Dispatchers.IO) {
            val taskId = ToolTaskRegistry.begin(if (move) "Dateien verschieben" else "Dateien kopieren", "${sourceState.displayPath} → ${paneState(targetPane).displayPath}", ::cancelFileTransfer)
            val names = mutableListOf<String>()
            val completedPaths = mutableSetOf<String>()
            try {
                selected.forEachIndexed { index, item ->
                    ensureActive()
                    _fileTransfer.update { it.copy(label = "${if (move) "Verschieben" else "Kopieren"}: ${item.name}", completed = index, bytes = 0) }
                    ToolTaskRegistry.progress(taskId, index * 100 / selected.size, item.name)
                    val copied = network.copy(item, targetPath, ::askConflict, { n -> _fileTransfer.update { it.copy(bytes = n) } }, { this@launch.ensureActive() })
                    if (copied) {
                        if (move) deleteItem(item, recycleOverride = false)
                        completedPaths += item.path
                        names += item.name
                    }
                    _fileTransfer.update { it.copy(completed = index + 1) }
                }
                _operationMessages.tryEmit("${names.size} Element(e) ${if (move) "verschoben" else "kopiert"}")
                ToolTaskRegistry.finish(taskId, message = "${names.size} Element(e) übertragen", outputPath = if (!FtpLocation.isRemote(targetPath) && !SafFileSystem.isSafPath(targetPath)) targetPath else null)
            } catch (error: Throwable) {
                ToolTaskRegistry.finish(taskId, if (error is CancellationException || error is TransferCancelledException || !isActive) ToolTaskStatus.CANCELLED else ToolTaskStatus.FAILED, error.message ?: "Übertragung fehlgeschlagen")
                _operationMessages.tryEmit(if (error is CancellationException || error is TransferCancelledException || !isActive) "Übertragung abgebrochen" else "Übertragung fehlgeschlagen: ${error.message}")
            } finally {
                // Partial copies still change archive workspaces and must be included in an update.
                scheduleArchiveCommit(targetPane)
                if (move) scheduleArchiveCommit(fromPane)
                updatePaneState(fromPane) { it.copy(selectedPaths = it.selectedPaths - completedPaths) }
                refreshDirectory(fromPane)
                refreshDirectory(targetPane)
                refreshMountedArchiveStates()
                highlightTransferredItems(targetPane, names, targetPath)
                _fileTransfer.update { it.copy(running = false) }
                ActivePane.entries.forEach(::resumePendingReveal)
            }
        }
    }

    fun linkToOppositePane(fromPane: ActivePane, sourcePath: String) {
        val targetPane = if (fromPane == ActivePane.LEFT) ActivePane.RIGHT else ActivePane.LEFT
        if (currentArchiveSession(targetPane) != null) {
            _operationMessages.tryEmit("Symbolic links cannot be stored inside archives")
            return
        }
        val targetPath = paneState(targetPane).currentPath
        if (SafFileSystem.isSafPath(sourcePath) || SafFileSystem.isSafPath(targetPath)) {
            _operationMessages.tryEmit("Symbolic links are only available on filesystem storage")
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val source = File(sourcePath)
                if (!source.exists()) throw IOException("Source does not exist: $sourcePath")
                val destination = File(targetPath, source.name)
                if (destination.exists() || Files.isSymbolicLink(destination.toPath())) {
                    throw IOException("Target already exists: ${destination.name}")
                }
                Files.createSymbolicLink(destination.toPath(), source.toPath().toAbsolutePath())
            }.onSuccess {
                refreshDirectory(targetPane)
                _operationMessages.tryEmit("Link created")
            }.onFailure { e ->
                _operationMessages.tryEmit("Link failed: ${e.message ?: e.javaClass.simpleName}")
            }
        }
    }

    /**
     * Creates an archive using the options from the MT-style archive dialog.
     * The work runs off the UI thread and both panes are refreshed because the
     * destination may intentionally be the opposite pane.
     */
    fun createArchive(pane: ActivePane, request: ArchiveRequest) {
        val taskId = beginArchiveTask(ArchiveTaskKind.CREATE, "Archive", request.fileName)
        val job = viewModelScope.launch(Dispatchers.IO) {
            try {
                updateArchiveTask(taskId, 5, "Creating archive")
                val outputs = ArchiveEngine.create(request)
                scheduleArchiveCommit(ActivePane.LEFT)
                scheduleArchiveCommit(ActivePane.RIGHT)
                refreshDirectory(ActivePane.LEFT)
                refreshDirectory(ActivePane.RIGHT)
                clearSelection(pane)
                outputs.firstOrNull()?.let { revealOutput(pane, it.absolutePath) }
                val names = outputs.joinToString { it.name }
                _operationMessages.tryEmit("Created $names")
                finishArchiveTask(taskId, true, names)
            } catch (cancelled: CancellationException) {
                _archiveTasks.update { tasks -> tasks.map { if (it.id == taskId) it.copy(status = ArchiveTaskStatus.CANCELLED, message = "Cancelled") else it } }
                throw cancelled
            } catch (e: Throwable) {
                _operationMessages.tryEmit("Compression failed: ${e.message ?: e.javaClass.simpleName}")
                finishArchiveTask(taskId, false, e.message ?: "Compression failed")
            }
        }
        archiveTaskJobs[taskId] = job
    }

    fun submitArchivePassword(password: String) {
        val request = _archivePasswordRequest.value ?: return
        if (password.isBlank()) return
        _archivePasswordRequest.value = null
        when (request.purpose) {
            ArchivePasswordPurpose.OPEN -> openArchive(request.pane, request.archive, password)
            ArchivePasswordPurpose.EXTRACT -> request.extractRequest?.let { extractArchive(request.pane, it.copy(password = password)) }
        }
    }

    fun cancelArchivePasswordRequest() {
        _archivePasswordRequest.value = null
    }

    private fun isArchivePasswordFailure(error: Throwable): Boolean {
        var cursor: Throwable? = error
        while (cursor != null) {
            val text = (cursor.message ?: "").lowercase()
            if (text.contains("password") || text.contains("encrypted") || text.contains("encryption") ||
                text.contains("wrong password") || text.contains("bad decrypt") || text.contains("crc mismatch")) return true
            cursor = cursor.cause
        }
        return false
    }

    fun openArchive(pane: ActivePane, archive: File, password: String = "") {
        val taskId = beginArchiveTask(ArchiveTaskKind.OPEN, "Open archive", archive.name)
        updatePaneState(pane) { it.copy(isLoading = true, loadingProgress = 0, loadingLabel = "Öffne ${archive.name}") }
        val job = viewModelScope.launch(Dispatchers.IO) {
            try {
                if (!ArchiveEngine.supports(archive)) throw IOException("Unsupported archive: ${archive.name}")
                val current = currentArchiveSession(pane)
                val nested = current != null && isInside(archive, current.workspaceRoot)
                if (current != null && !nested) {
                    val state = archiveSessionManager.refresh(current.id) ?: current
                    if (state.state == ArchiveSessionState.DIRTY || state.state == ArchiveSessionState.FAILED) {
                        pendingArchiveOpen[pane] = PendingArchiveOpen(archive.canonicalFile, password)
                        _archiveUpdateRequest.value = ArchiveUpdateRequest(pane, state.archive.name, state.state, state.dirtyEntries, state.depth)
                        throw IOException("Current archive has pending changes")
                    }
                    if (!usedByOtherPane(pane, current.id)) archiveSessionManager.discard(current.id)
                    archiveSessionIds.remove(pane)
                }
                val parentId = if (nested && current != null) current.id else null
                val returnDir = remoteEdits[archive.canonicalPath]?.let { edit ->
                    if (edit.item.isFtp) FtpLocation.parent(edit.item.path) else documentParentByFile[archive.canonicalPath] ?: paneState(pane).currentPath
                }
                    ?: archive.parentFile?.absolutePath ?: SharedStorage.primaryRoot().absolutePath
                val session = archiveSessionManager.open(archive, returnDir, password, parentId) { progress ->
                    this@launch.ensureActive()
                    updateArchiveTask(taskId, progress, "Opening ${archive.name}")
                    updatePaneState(pane) { state -> state.copy(isLoading = true, loadingProgress = progress.coerceIn(0, 100), loadingLabel = "Öffne ${archive.name}") }
                }
                archiveSessionIds[pane] = session.id
                publishArchiveStates()
                updatePaneState(pane) { it.copy(archiveFilePath = session.archive.absolutePath, archiveDisplayPath = archiveDisplayName(session), archiveRootPath = session.workspaceRoot.absolutePath, highlightedItemName = null, highlightedItemPath = null) }
                loadDirectory(pane, session.workspaceRoot.absolutePath)
                _operationMessages.tryEmit(if (nested) "Opened nested archive ${archive.name}" else "Opened ${archive.name}")
                finishArchiveTask(taskId, true, archive.name)
            } catch (cancelled: CancellationException) {
                updatePaneState(pane) { it.copy(isLoading = false, loadingProgress = null, loadingLabel = null) }
                _archiveTasks.update { tasks -> tasks.map { if (it.id == taskId) it.copy(status = ArchiveTaskStatus.CANCELLED, message = "Cancelled") else it } }
                throw cancelled
            } catch (error: Throwable) {
                updatePaneState(pane) { it.copy(isLoading = false, loadingProgress = null, loadingLabel = null) }
                if (error.message != "Current archive has pending changes") {
                    if (isArchivePasswordFailure(error)) {
                        _archivePasswordRequest.value = ArchivePasswordRequest(pane, archive.canonicalFile, ArchivePasswordPurpose.OPEN, message = error.message)
                    } else {
                        _operationMessages.tryEmit("Archive open failed: ${error.message ?: error.javaClass.simpleName}")
                    }
                }
                finishArchiveTask(taskId, false, error.message ?: "Archive open failed")
            }
        }
        archiveTaskJobs[taskId] = job
    }

    fun extractArchive(pane: ActivePane, request: ArchiveExtractRequest) {
        val taskId = beginArchiveTask(ArchiveTaskKind.EXTRACT, "Extract", request.archive.name)
        val job = viewModelScope.launch(Dispatchers.IO) {
            try {
                beginTransferBatch()
                updateArchiveTask(taskId, 5, "Extracting ${request.archive.name}")
                val destination = ArchiveEngine.extract(
                    request = request,
                    onProgress = { progress ->
                        this@launch.ensureActive()
                        updateArchiveTask(taskId, progress.coerceIn(0, 100), "Extracting ${request.archive.name}")
                    },
                    conflictResolver = { conflict ->
                        when (runBlocking {
                            askConflict(
                                name = conflict.entryName.substringAfterLast('/'),
                                source = "${request.archive.absolutePath}!/${conflict.entryName}",
                                destination = conflict.destination.absolutePath,
                                directory = conflict.directory,
                            )
                        }) {
                            FileConflictAction.OVERWRITE -> ArchiveConflictAction.OVERWRITE
                            FileConflictAction.SKIP -> ArchiveConflictAction.SKIP
                            FileConflictAction.KEEP_BOTH -> ArchiveConflictAction.KEEP_BOTH
                            FileConflictAction.CANCEL -> ArchiveConflictAction.CANCEL
                        }
                    },
                )
                markArchivesAffectedBy(request.archive, destination)
                refreshDirectory(ActivePane.LEFT)
                refreshDirectory(ActivePane.RIGHT)
                revealOutput(pane, destination.absolutePath)
                _operationMessages.tryEmit("Extracted to ${destination.absolutePath}")
                finishArchiveTask(taskId, true, destination.absolutePath)
            } catch (cancelled: CancellationException) {
                _archiveTasks.update { tasks -> tasks.map { if (it.id == taskId) it.copy(status = ArchiveTaskStatus.CANCELLED, message = "Cancelled") else it } }
                throw cancelled
            } catch (error: Throwable) {
                if (error.message == "Extraction cancelled") {
                    _operationMessages.tryEmit("Extraction cancelled")
                    _archiveTasks.update { tasks -> tasks.map { if (it.id == taskId) it.copy(status = ArchiveTaskStatus.CANCELLED, message = "Cancelled") else it } }
                    archiveTaskJobs.remove(taskId)
                    viewModelScope.launch { delay(1000); _archiveTasks.update { tasks -> tasks.filterNot { it.id == taskId && it.isTerminal } } }
                    return@launch
                } else if (isArchivePasswordFailure(error)) {
                    _archivePasswordRequest.value = ArchivePasswordRequest(
                        pane = pane,
                        archive = request.archive.canonicalFile,
                        purpose = ArchivePasswordPurpose.EXTRACT,
                        extractRequest = request.copy(password = ""),
                        message = error.message,
                    )
                } else {
                    _operationMessages.tryEmit("Extraction failed: ${error.message ?: error.javaClass.simpleName}")
                }
                finishArchiveTask(taskId, false, error.message ?: "Extraction failed")
            }
        }
        archiveTaskJobs[taskId] = job
    }

    fun dismissArchiveTestReport() { _archiveTestReport.value = null }

    fun testOpenArchive(pane: ActivePane) {
        val session = currentArchiveSession(pane) ?: return
        if (paneState(pane).isLoading) { _operationMessages.tryEmit("Laufende Archiv-Aktion zuerst beenden"); return }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val result = ToolTaskRegistry.run("Archiv testen", archiveDisplayName(session)) { task ->
                    val checked = archiveSessionManager.test(session.id,
                        { progress, name -> this@launch.ensureActive(); ToolTaskRegistry.progress(task, progress, name) },
                        { this@launch.ensureActive() })
                    ToolTaskRegistry.progress(task, 100, checked.entries.toString() + " Einträge • " + checked.bytesRead + " Bytes\n" + checked.checks)
                    checked
                }
                _archiveTestReport.value = ArchiveTestReport(session.archive.name, result)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _archiveTestReport.value = ArchiveTestReport(session.archive.name, error = error.message ?: "Archivtest fehlgeschlagen")
            }
        }
    }

    fun reloadArchiveCharset(pane: ActivePane, charset: String) {
        if (_fileTransfer.value.running || mutationRunning.get() || searchJobs.values.any { it.isActive } ||
            _archiveTasks.value.any { !it.isTerminal } || paneState(pane).isLoading) {
            _operationMessages.tryEmit("Laufende Datei-, Such- oder Archivaufgabe zuerst beenden"); return
        }
        val session = currentArchiveSession(pane) ?: return
        val affected = ActivePane.entries.filter { currentArchiveSession(it)?.id == session.id }
        val relativePaths = affected.associateWith { runCatching {
            File(paneState(it).currentPath).relativeTo(session.workspaceRoot).path
        }.getOrDefault("") }
        affected.forEach { setPaneBusy(it, true, "Archiv mit anderem Zeichensatz laden") }
        viewModelScope.launch(Dispatchers.IO) {
            var startedDirectoryLoads = false
            try {
                val loaded = ToolTaskRegistry.run("Archiv-Zeichensatz", session.archive.name + " • " + charset.ifBlank { "Automatisch" }) { task ->
                    archiveSessionManager.reloadCharset(session.id, charset, checkCancelled = { this@launch.ensureActive() }) { progress ->
                        this@launch.ensureActive()
                        ToolTaskRegistry.progress(task, progress, "Dateinamen neu laden")
                    }
                }
                publishArchiveStates()
                affected.filter { currentArchiveSession(it)?.id == session.id }.forEach { target ->
                    val previous = File(loaded.workspaceRoot, relativePaths[target].orEmpty())
                    val path = previous.takeIf { it.isDirectory } ?: loaded.workspaceRoot
                    updatePaneState(target) { it.copy(selectedPaths = emptySet(), recentlyChangedPaths = emptySet(),
                        highlightedItemName = null, highlightedItemPath = null, searchQuery = "", searchResultsLabel = null) }
                    searchParents.remove(target); searchSpecs.remove(target)
                    val back = if (target == ActivePane.LEFT) leftBackStack else rightBackStack
                    val forward = if (target == ActivePane.LEFT) leftForwardStack else rightForwardStack
                    listOf(back, forward).forEach { history -> history.removeAll {
                        isInside(File(it), loaded.workspaceRoot) && !File(it).isDirectory
                    } }
                    loadDirectory(target, path.absolutePath)
                }
                startedDirectoryLoads = true
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _operationMessages.tryEmit(error.message ?: "Archiv konnte nicht neu geladen werden")
            } finally {
                if (!startedDirectoryLoads) affected.filter { currentArchiveSession(it)?.id == session.id }.forEach { setPaneBusy(it, false) }
                publishArchiveStates()
            }
        }
    }

    /** Explicit save action for all mounted archives. Lifecycle events no longer auto-save. */
    fun updateArchive(pane: ActivePane) {
        if (_fileTransfer.value.running || mutationRunning.get() || searchJobs.values.any { it.isActive } || _archiveTasks.value.any { !it.isTerminal }) { _operationMessages.tryEmit("Laufende Datei-, Such- oder Archivaufgabe zuerst beenden"); return }
        val session = currentArchiveSession(pane) ?: return
        val id = beginArchiveTask(ArchiveTaskKind.UPDATE, "Archiv aktualisieren", session.archive.name)
        val job = viewModelScope.launch(Dispatchers.IO) {
            ActivePane.entries.filter { currentArchiveSession(it)?.id == session.id }.forEach { setPaneBusy(it, true, "Archiv aktualisieren") }
            try {
                archiveSessionManager.commit(session.id) { progress -> ensureActive(); updateArchiveTask(id, progress); publishArchiveStates() }
                finishArchiveTask(id, true, session.archive.absolutePath)
            } catch (cancelled: CancellationException) {
                _archiveTasks.update { tasks -> tasks.map { if (it.id == id) it.copy(status = ArchiveTaskStatus.CANCELLED, message = "Abgebrochen") else it } }
                throw cancelled
            } catch (error: Exception) { finishArchiveTask(id, false, error.message ?: "Archivaktualisierung fehlgeschlagen") }
            finally {
                archiveTaskJobs.remove(id)
                ActivePane.entries.filter { currentArchiveSession(it)?.id == session.id }.forEach { setPaneBusy(it, false) }
                refreshMountedArchiveStates()
            }
        }
        archiveTaskJobs[id] = job
    }

    fun commitMountedArchives() {
        viewModelScope.launch(Dispatchers.IO) {
            ActivePane.entries.forEach { pane ->
                val session = currentArchiveSession(pane) ?: return@forEach
                runCatching { archiveSessionManager.commit(session.id) }
                    .onSuccess { _operationMessages.tryEmit("Saved ${it.archive.name}") }
                    .onFailure { error -> _operationMessages.tryEmit("Archive save failed: ${error.message ?: error.javaClass.simpleName}") }
            }
        }
    }

    fun refreshMountedArchiveStates() {
        viewModelScope.launch(Dispatchers.IO) {
            if (_remoteEditRequest.value == null) {
                remoteEdits.values.firstOrNull { edit -> edit.file.isFile && runCatching { network.sha256(edit.file) != edit.localHash }.getOrDefault(false) }
                    ?.let { _remoteEditRequest.value = it }
            }
            archiveSessionIds.values.toSet().forEach { id -> runCatching { archiveSessionManager.refresh(id) } }
            publishArchiveStates()
            ActivePane.entries.filter { !paneState(it).isLoading }.forEach(::refreshDirectory)
        }
    }

    fun requestCloseArchive(pane: ActivePane) {
        if (_fileTransfer.value.running || mutationRunning.get() || _archiveTasks.value.any { !it.isTerminal }) { _operationMessages.tryEmit("Laufende Datei- oder Archivaufgabe zuerst beenden"); return }
        viewModelScope.launch(Dispatchers.IO) {
            val session = currentArchiveSession(pane) ?: return@launch
            if (usedByOtherPane(pane, session.id)) { finishArchiveClose(pane, session, committed = false); return@launch }
            val refreshed = archiveSessionManager.refresh(session.id) ?: session
            publishArchiveStates()
            if (refreshed.state == ArchiveSessionState.DIRTY || refreshed.state == ArchiveSessionState.FAILED) {
                _archiveUpdateRequest.value = ArchiveUpdateRequest(pane, refreshed.archive.name, refreshed.state, refreshed.dirtyEntries, refreshed.depth)
            } else finishArchiveClose(pane, refreshed, committed = false)
        }
    }

    fun resolveArchiveUpdate(decision: ArchiveUpdateDecision) {
        val request = _archiveUpdateRequest.value ?: return
        if (decision == ArchiveUpdateDecision.CANCEL) {
            pendingArchiveOpen.remove(request.pane)
            pendingReveals.remove(request.pane)
            exitAction = null
            _archiveUpdateRequest.value = null
            return
        }
        _archiveUpdateRequest.value = null
        viewModelScope.launch(Dispatchers.IO) {
            val session = currentArchiveSession(request.pane) ?: return@launch
            when (decision) {
                ArchiveUpdateDecision.UPDATE -> {
                    val taskId = beginArchiveTask(ArchiveTaskKind.UPDATE, "Update archive", session.archive.name)
                    coroutineContext[Job]?.let { archiveTaskJobs[taskId] = it }
                    try {
                        val committed = archiveSessionManager.commit(session.id) { progress ->
                            this@launch.ensureActive()
                            updateArchiveTask(taskId, progress, "Repacking ${session.archive.name}")
                        }
                        _operationMessages.tryEmit("Updated ${committed.archive.name}")
                        finishArchiveTask(taskId, true, committed.archive.name)
                        finishArchiveClose(request.pane, committed, committed = true)
                        resumePendingArchiveOpen(request.pane)
                    } catch (cancelled: CancellationException) {
                        _archiveTasks.update { tasks -> tasks.map { if (it.id == taskId) it.copy(status = ArchiveTaskStatus.CANCELLED, message = "Cancelled") else it } }
                        archiveTaskJobs.remove(taskId)
                        val failed = archiveSessionManager.refresh(session.id) ?: session
                        _archiveUpdateRequest.value = ArchiveUpdateRequest(request.pane, failed.archive.name, failed.state, failed.dirtyEntries, failed.depth)
                        throw cancelled
                    } catch (error: Throwable) {
                        finishArchiveTask(taskId, false, error.message ?: "Archive update failed")
                        _operationMessages.tryEmit("Archive update failed: ${error.message ?: error.javaClass.simpleName}")
                        val failed = archiveSessionManager.refresh(session.id) ?: session
                        _archiveUpdateRequest.value = ArchiveUpdateRequest(request.pane, failed.archive.name, failed.state, failed.dirtyEntries, failed.depth)
                    }
                }
                ArchiveUpdateDecision.DISCARD -> {
                    finishArchiveClose(request.pane, session, committed = false)
                    resumePendingArchiveOpen(request.pane)
                }
                ArchiveUpdateDecision.CANCEL -> Unit
            }
        }
    }

    fun closeArchive(pane: ActivePane, saveChanges: Boolean = true) {
        viewModelScope.launch(Dispatchers.IO) {
            val session = currentArchiveSession(pane) ?: return@launch
            if (saveChanges) {
                val refreshed = archiveSessionManager.refresh(session.id) ?: session
                if (refreshed.state == ArchiveSessionState.DIRTY || refreshed.state == ArchiveSessionState.FAILED) {
                    _archiveUpdateRequest.value = ArchiveUpdateRequest(pane, refreshed.archive.name, refreshed.state, refreshed.dirtyEntries, refreshed.depth)
                    return@launch
                }
            }
            finishArchiveClose(pane, session, committed = false)
        }
    }

    private fun finishArchiveClose(pane: ActivePane, session: ArchiveSessionSnapshot, committed: Boolean) {
        val parentId = session.parentId
        if (!usedByOtherPane(pane, session.id)) {
            if (committed) archiveSessionManager.closeAfterCommit(session.id) else archiveSessionManager.discard(session.id)
        }
        if (parentId != null) {
            val parent = archiveSessionManager.get(parentId)
            if (parent != null) {
                archiveSessionIds[pane] = parent.id
                updatePaneState(pane) {
                    it.copy(
                        archiveFilePath = parent.archive.absolutePath,
                        archiveDisplayPath = archiveDisplayName(parent),
                        archiveRootPath = parent.workspaceRoot.absolutePath,
                        highlightedItemName = session.archive.name,
                    )
                }
                val target = session.archive.parentFile?.takeIf { isInside(it, parent.workspaceRoot) } ?: parent.workspaceRoot
                loadDirectory(pane, target.absolutePath, isHistoryAction = true, highlightName = session.archive.name)
                publishArchiveStates()
                refreshMountedArchiveStates()
                resumePendingReveal(pane)
                continueExit()
                return
            }
        }
        archiveSessionIds.remove(pane)
        updatePaneState(pane) { it.copy(archiveFilePath = null, archiveDisplayPath = null, archiveRootPath = null, highlightedItemName = session.archive.name) }
        val returnDirectory = remoteEdits[session.archive.canonicalPath]?.let { edit ->
            if (edit.item.isFtp) FtpLocation.parent(edit.item.path) else documentParentByFile[edit.file.canonicalPath]
        } ?: session.returnDirectory
        loadDirectory(pane, returnDirectory, isHistoryAction = true, highlightName = session.archive.name)
        publishArchiveStates()
        refreshMountedArchiveStates()
        resumePendingReveal(pane)
        continueExit()
    }

    fun requestExit(action: () -> Unit) {
        if (_fileTransfer.value.running || mutationRunning.get() || _archiveTasks.value.any { !it.isTerminal }) {
            _operationMessages.tryEmit("Laufende Datei- oder Archivaufgabe zuerst beenden"); return
        }
        searchJobs.values.forEach { it.cancel() }
        pendingReveals.clear(); pendingArchiveOpen.clear()
        exitAction = action
        continueExit()
    }

    private fun continueExit() {
        val action = exitAction ?: return
        val pane = ActivePane.entries.firstOrNull { currentArchiveSession(it) != null }
        if (pane != null) requestCloseArchive(pane)
        else viewModelScope.launch(Dispatchers.IO) {
            val edit = remoteEdits.values.firstOrNull { it.file.isFile && runCatching { network.sha256(it.file) != it.localHash }.getOrDefault(false) }
            if (edit != null) _remoteEditRequest.value = edit
            else withContext(Dispatchers.Main) { if (exitAction === action) { exitAction = null; action() } }
        }
    }

    private fun resumePendingArchiveOpen(pane: ActivePane) {
        val pending = pendingArchiveOpen.remove(pane) ?: return
        openArchive(pane, pending.archive, pending.password)
    }

    fun navigateToDisplayPath(pane: ActivePane, value: String) {
        val text = value.trim()
        val session = currentArchiveSession(pane)
        if (session != null) {
            val archiveName = listOfNotNull(paneState(pane).archiveDisplayPath, session.archive.absolutePath).firstOrNull { text == "$it!" || text.startsWith("$it!/") }
            if (archiveName != null) {
                val relative = if (text == "$archiveName!") "" else text.removePrefix("$archiveName!/").trimStart('/', '\\')
                val target = if (relative.isBlank()) session.workspaceRoot else File(session.workspaceRoot, relative)
                if (!isInside(target, session.workspaceRoot)) {
                    _operationMessages.tryEmit("Path is outside the opened archive")
                    return
                }
                navigateToDirectPath(pane, target.absolutePath)
                return
            }
            _operationMessages.tryEmit("Leave the archive with .. before jumping to another filesystem path")
            return
        }
        val state = paneState(pane)
        if (text == state.displayPath) return
        if (state.isFtpView && !FtpLocation.isRemote(text)) {
            runCatching {
                val uri = Uri.parse(text)
                val profile = network.connection(state.currentPath).profile
                val path = if (text.startsWith('/')) text else {
                    if (uri.scheme !in setOf("ftp", "ftps") || !uri.host.equals(profile.host, true) || (uri.port >= 0 && uri.port != profile.port)) throw IOException("FTP-Host über das Verbindungsprofil wechseln")
                    uri.path ?: "/"
                }
                loadDirectory(pane, FtpLocation.uri(FtpLocation.session(state.currentPath), path))
            }.onFailure { _operationMessages.tryEmit(it.message ?: "Ungültiger FTP-Pfad") }
            return
        }
        navigateToDirectPath(pane, text)
    }

    private fun scheduleArchiveCommit(pane: ActivePane) {
        val session = currentArchiveSession(pane) ?: return
        val relative = paneState(pane).currentPath.takeIf { isInside(File(it), session.workspaceRoot) }
            ?.let { runCatching { File(it).relativeTo(session.workspaceRoot).invariantSeparatorsPath }.getOrNull() }
        archiveSessionManager.markDirty(session.id, relative)
        publishArchiveStates()
    }

    private fun markArchivesAffectedBy(vararg files: File) {
        ActivePane.entries.forEach { pane ->
            val session = currentArchiveSession(pane) ?: return@forEach
            if (files.any { isInside(it, session.workspaceRoot) }) archiveSessionManager.markDirty(session.id)
        }
        publishArchiveStates()
    }

    private fun isInside(file: File, root: File): Boolean = runCatching {
        val candidate = file.canonicalFile
        val base = root.canonicalFile
        candidate == base || candidate.path.startsWith(base.path + File.separator)
    }.getOrDefault(false)

    private fun sameFile(first: File, second: File): Boolean = runCatching {
        first.canonicalFile == second.canonicalFile
    }.getOrDefault(first.absolutePath == second.absolutePath)

    private fun resumePendingReveal(pane: ActivePane) {
        val target = pendingReveals.remove(pane) ?: return
        highlightDocument(pane, target.first, target.second)
    }

    fun highlightDocument(pane: ActivePane, directory: String, name: String) {
        val archive = currentArchiveSession(pane)
        if (archive != null && !isInside(File(directory), archive.workspaceRoot)) {
            pendingReveals[pane] = directory to name
            requestCloseArchive(pane)
            return
        }
        loadDirectory(pane, directory, highlightName = name)
    }

    private fun highlightTransferredItems(pane: ActivePane, names: List<String>, expectedPath: String = paneState(pane).currentPath) {
        if (names.isEmpty()) return
        viewModelScope.launch {
            val flow = if (pane == ActivePane.LEFT) leftPaneState else rightPaneState
            val loaded = withTimeoutOrNull(60_000) {
                flow.first { it.currentPath != expectedPath || (!it.isLoading && it.items.any { item -> item.name in names }) }
            } ?: return@launch
            if (loaded.currentPath != expectedPath) return@launch
            val matches = loaded.items.filter { it.name in names }
            val paths = matches.map { it.path }.toSet()
            val highlighted = matches.firstOrNull() ?: return@launch
            updatePaneState(pane) { state ->
                state.copy(highlightedItemName = highlighted.name, highlightedItemPath = highlighted.path,
                    highlightEvent = state.highlightEvent + 1, recentlyChangedPaths = state.recentlyChangedPaths + paths)
            }
            val event = paneState(pane).highlightEvent
            delay(RECENT_HIGHLIGHT_MS)
            updatePaneState(pane) { state ->
                if (state.currentPath != expectedPath || state.highlightEvent != event) state else state.copy(
                    highlightedItemName = if (state.highlightedItemPath in paths) null else state.highlightedItemName,
                    highlightedItemPath = if (state.highlightedItemPath in paths) null else state.highlightedItemPath,
                    recentlyChangedPaths = state.recentlyChangedPaths - paths)
            }
        }
    }

    private suspend fun copyItemToTarget(item: FileItem, targetPath: String): Boolean {
        val targetSaf = SafFileSystem.isSafPath(targetPath)
        when {
            item.isSaf && targetSaf -> {
                val targetDir = Uri.parse(targetPath)
                var targetName = item.name
                SafFileSystem.findChildByName(app, targetDir, targetName)?.let { existing ->
                    when (askConflict(item, "$targetPath/$targetName")) {
                        FileConflictAction.SKIP -> return false
                        FileConflictAction.CANCEL -> throw TransferCancelledException()
                        FileConflictAction.KEEP_BOTH -> targetName = SafFileSystem.uniqueChildName(app, targetDir, targetName)
                        FileConflictAction.OVERWRITE -> SafFileSystem.delete(app, existing)
                    }
                }
                SafFileSystem.copySafToSaf(app, Uri.parse(item.path), item.name, item.isDirectory, targetDir, targetName)
            }
            item.isSaf && !targetSaf -> {
                val targetDir = File(targetPath)
                var target = File(targetDir, item.name)
                if (target.exists()) {
                    when (askConflict(item, target.absolutePath)) {
                        FileConflictAction.SKIP -> return false
                        FileConflictAction.CANCEL -> throw TransferCancelledException()
                        FileConflictAction.KEEP_BOTH -> target = uniqueLocalTarget(target)
                        FileConflictAction.OVERWRITE -> deleteExistingLocal(target)
                    }
                }
                SafFileSystem.copySafToFileSystem(app, Uri.parse(item.path), item.name, item.isDirectory, targetDir, target.name)
            }
            !item.isSaf && targetSaf -> {
                val source = File(item.path)
                val targetDir = Uri.parse(targetPath)
                var targetName = item.name
                SafFileSystem.findChildByName(app, targetDir, targetName)?.let { existing ->
                    when (askConflict(item, "$targetPath/$targetName")) {
                        FileConflictAction.SKIP -> return false
                        FileConflictAction.CANCEL -> throw TransferCancelledException()
                        FileConflictAction.KEEP_BOTH -> targetName = SafFileSystem.uniqueChildName(app, targetDir, targetName)
                        FileConflictAction.OVERWRITE -> SafFileSystem.delete(app, existing)
                    }
                }
                if (source.isDirectory) SafFileSystem.copyDirectoryToSaf(app, source, targetDir, targetName)
                else SafFileSystem.copyFileToSaf(app, source, targetDir, targetName)
            }
            else -> {
                val source = File(item.path)
                var dest = File(targetPath, item.name)
                ensureTransferTargetIsSafe(source, dest)
                if (dest.exists()) {
                    when (askConflict(item, dest.absolutePath)) {
                        FileConflictAction.SKIP -> return false
                        FileConflictAction.CANCEL -> throw TransferCancelledException()
                        FileConflictAction.KEEP_BOTH -> dest = uniqueLocalTarget(dest)
                        FileConflictAction.OVERWRITE -> deleteExistingLocal(dest)
                    }
                }
                if (!copyLocalToLocal(source, dest)) throw IOException("Could not copy ${source.name}")
            }
        }
        return true
    }

    private fun copyLocalToLocal(source: File, dest: File): Boolean {
        if (source.isDirectory) {
            val ok = source.copyRecursively(dest, overwrite = false)
            if (ok && ExplorerPreferences.current(app).preserveFileTime) preserveTimestamps(source, dest)
            return ok
        }
        dest.parentFile?.mkdirs()
        source.copyTo(dest, overwrite = false)
        if (ExplorerPreferences.current(app).preserveFileTime) dest.setLastModified(source.lastModified())
        return true
    }

    private fun deleteExistingLocal(file: File) {
        val ok = if (file.isDirectory) file.deleteTreeSafely() else file.delete()
        if (!ok && file.exists()) throw IOException("Could not replace ${file.name}")
    }

    private fun uniqueLocalTarget(initial: File): File {
        if (!initial.exists()) return initial
        val name = initial.name
        val dot = name.lastIndexOf('.')
        val base = if (dot > 0) name.substring(0, dot) else name
        val ext = if (dot > 0) name.substring(dot) else ""
        var i = 1
        var candidate: File
        do {
            candidate = File(initial.parentFile, "$base ($i)$ext")
            i++
        } while (candidate.exists())
        return candidate
    }

    private fun deleteItem(item: FileItem, recycleOverride: Boolean? = null) {
        if (item.isFtp) { network.delete(item.path); return }
        val prefs = ExplorerPreferences.current(app)
        val useRecycle = recycleOverride ?: prefs.moveToRecycleBinByDefault
        if (item.isSaf) {
            if (prefs.recycleBinEnabled && useRecycle) {
                val recycleRoot = File(prefs.customWorkspace, ".RecycleBin").apply { mkdirs() }
                val targetName = uniqueLocalTarget(File(recycleRoot, item.name)).name
                SafFileSystem.copySafToFileSystem(
                    app,
                    Uri.parse(item.path),
                    item.name,
                    item.isDirectory,
                    recycleRoot,
                    targetName,
                )
                SafFileSystem.delete(app, Uri.parse(item.path))
            } else {
                SafFileSystem.delete(app, Uri.parse(item.path))
            }
            return
        }
        val file = File(item.path)
        val externalRoot = SharedStorage.primaryRoot().absolutePath
        val recycleRootPath = File(prefs.customWorkspace, ".RecycleBin").absolutePath
        val canRecycle = prefs.recycleBinEnabled && useRecycle &&
            file.absolutePath.startsWith(externalRoot + File.separator) &&
            file.absolutePath != recycleRootPath &&
            !file.absolutePath.startsWith(recycleRootPath + File.separator)
        if (canRecycle) {
            val recycleRoot = File(prefs.customWorkspace, ".RecycleBin")
            recycleRoot.mkdirs()
            var target = File(recycleRoot, file.name)
            var suffix = 1
            while (target.exists()) {
                target = File(recycleRoot, "${file.nameWithoutExtension}_$suffix${if (file.extension.isNotBlank()) ".${file.extension}" else ""}")
                suffix++
            }
            if (!file.renameTo(target)) {
                if (Files.isSymbolicLink(file.toPath())) throw IOException("Verknüpfungen können auf diesem Speicher nicht in den Papierkorb verschoben werden")
                if (file.isDirectory) {
                    Files.walk(file.toPath()).use { paths ->
                        if (paths.anyMatch { Files.isSymbolicLink(it) }) throw IOException("Ordner enthält Verknüpfungen; auf diesem Speicher ist nur direktes Löschen möglich")
                    }
                    if (!file.copyRecursively(target, overwrite = false)) throw IOException("Could not move ${item.name} to recycle bin")
                    if (!file.deleteTreeSafely()) throw IOException("Could not remove ${item.name} after recycling")
                } else {
                    file.copyTo(target, overwrite = false)
                    if (!file.delete()) throw IOException("Could not remove ${item.name} after recycling")
                }
            }
            return
        }
        val ok = if (file.isDirectory) file.deleteTreeSafely() else file.delete()
        if (!ok && file.exists()) throw IOException("Could not delete ${item.name}")
    }

    fun isSafPane(pane: ActivePane): Boolean = SafFileSystem.isSafPath(paneState(pane).currentPath)

    private fun ensureTransferTargetIsSafe(source: File, destination: File) {
        val sourceCanonical = source.canonicalFile
        val destinationCanonical = destination.canonicalFile
        if (sourceCanonical == destinationCanonical) {
            throw IOException("Source and destination are the same")
        }
        if (source.isDirectory && destinationCanonical.path.startsWith(sourceCanonical.path + File.separator)) {
            throw IOException("Cannot copy or move a directory into itself")
        }
    }

    // --- Batch Delete ---
    fun deleteSelected(pane: ActivePane, recycleOverride: Boolean? = null) {
        val state = paneState(pane)
        val items = state.selectedPaths.mapNotNull { path -> state.items.firstOrNull { it.path == path } }
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { ToolTaskRegistry.run("Dateien löschen", "${items.size} Element(e)", network::abortAll) { taskId -> items.forEachIndexed { index, item -> ensureActive(); ToolTaskRegistry.progress(taskId, index * 100 / items.size.coerceAtLeast(1), item.name); deleteItem(item, recycleOverride) } } }
                .onSuccess {
                    scheduleArchiveCommit(pane)
                    refreshDirectory(pane)
                    clearSelection(pane)
                }
                .onFailure { scheduleArchiveCommit(pane); refreshMountedArchiveStates(); _operationMessages.tryEmit("Delete failed: ${it.message ?: it.javaClass.simpleName}") }
        }
    }

    // --- Create New File or Folder ---
    fun createNewItem(pane: ActivePane, name: String, isFolder: Boolean): String? {
        val currentPath = paneState(pane).currentPath
        try { FtpPaths.name(name) } catch (e: IOException) { return e.message }
        if (FtpLocation.isRemote(currentPath)) {
            viewModelScope.launch(Dispatchers.IO) {
                runCatching { ToolTaskRegistry.run("FTP-Datei erstellen", name, network::abortAll) { network.create(currentPath, name, isFolder) } }.onSuccess { refreshDirectory(pane) }
                    .onFailure { _operationMessages.tryEmit("Erstellen fehlgeschlagen: ${it.message}") }
            }
            return "FTP: Erstellen gestartet"
        }
        return runCatching {
            if (SafFileSystem.isSafPath(currentPath)) {
                SafFileSystem.create(app, Uri.parse(currentPath), name, isFolder)
            } else {
                val targetFile = File(currentPath, name)
                if (targetFile.exists()) return "An item with this name already exists!"
                val success = if (isFolder) targetFile.mkdirs() else targetFile.createNewFile()
                if (!success) throw IOException("Could not create $name")
                scheduleArchiveCommit(pane)
            }
            refreshDirectory(pane)
            "Created ${if (isFolder) "folder" else "file"} successfully."
        }.getOrElse { "Failed to create ${if (isFolder) "folder" else "file"}: ${it.message}" }
    }

    fun clearSelection(pane: ActivePane) {
        updatePaneState(pane) { it.copy(selectedPaths = emptySet()) }
    }

    fun renameItem(pane: ActivePane, oldPath: String, newName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { ToolTaskRegistry.run("Datei umbenennen", "$oldPath → $newName", network::abortAll) {
                FtpPaths.name(newName)
                if (FtpLocation.isRemote(oldPath)) {
                    network.rename(oldPath, newName)
                } else if (SafFileSystem.isSafPath(oldPath)) {
                    SafFileSystem.rename(app, Uri.parse(oldPath), newName)
                } else {
                    val oldFile = File(oldPath)
                    if (!oldFile.exists()) throw IOException("Source no longer exists")
                    val newFile = File(oldFile.parent, newName)
                    if (newFile.exists()) throw IOException("Zielname existiert bereits")
                    if (!oldFile.renameTo(newFile)) throw IOException("Rename failed")
                    scheduleArchiveCommit(pane)
                }
            } }.onSuccess { refreshDirectory(pane); refreshMountedArchiveStates() }
                .onFailure { _operationMessages.tryEmit("Rename failed: ${it.message ?: it.javaClass.simpleName}") }
        }
    }

    fun setSearchQuery(pane: ActivePane, query: String) {
        updatePaneState(pane) { it.copy(searchQuery = query) }
    }

    private fun listFilesAt(path: String): List<FileItem> = when {
        FtpLocation.isRemote(path) -> network.list(path)
        SafFileSystem.isSafPath(path) -> SafFileSystem.list(app, Uri.parse(path))
        else -> File(path).listFiles()?.map { FileItem(it) } ?: throw IOException("Ordner kann nicht gelesen werden: $path")
    }

    fun searchHistory(): List<String> = runCatching {
        val json = org.json.JSONArray(panePreferences.getString("search_history", "[]"))
        List(json.length()) { json.getString(it) }
    }.getOrDefault(emptyList())

    fun clearSearchHistory() { panePreferences.edit().remove("search_history").apply() }

    fun clearAdvancedSearch(pane: ActivePane) {
        searchJobs.remove(pane)?.cancel()
        loadDirectory(pane, paneState(pane).currentPath, isHistoryAction = true)
    }

    fun advancedSearch(pane: ActivePane, spec: FileWorkflow.SearchSpec) {
        if (_fileTransfer.value.running || mutationRunning.get() || _archiveTasks.value.any { !it.isTerminal }) {
            _operationMessages.tryEmit("Datei- oder Archiv-Aktion zuerst beenden"); return
        }
        searchJobs.remove(pane)?.cancel()
        val start = paneState(pane)
        searchSpecs[pane] = spec
        val generation = loadGeneration.getValue(pane).incrementAndGet()
        val history = (listOf(spec.query.ifEmpty { spec.content }) + searchHistory()).distinct().take(40)
        panePreferences.edit().putString("search_history", org.json.JSONArray(history).toString()).apply()
        updatePaneState(pane) { it.copy(isLoading = true, loadingLabel = "Suche läuft", selectedPaths = emptySet()) }
        val job = viewModelScope.launch(Dispatchers.IO) {
            try {
                ToolTaskRegistry.run("Erweiterte Suche", start.displayPath) { task ->
                    val coroutine = currentCoroutineContext()
                    val matches = mutableListOf<FileItem>()
                    val parents = mutableMapOf<String, String>()
                    val queue = java.util.ArrayDeque<Pair<String, Int>>()
                    val visited = mutableSetOf<String>()
                    queue.add(start.currentPath to 0)
                    var scanned = 0; var skipped = 0; var limited = false
                    while (queue.isNotEmpty()) {
                        coroutine.ensureActive()
                        val (directory, depth) = queue.removeFirst()
                        if (!visited.add(directory)) continue
                        val listing = try { listFilesAt(directory) } catch (error: IOException) { skipped++; continue }
                        for (item in listing) {
                            coroutine.ensureActive()
                            if (!item.isSaf && !item.isFtp && Files.isSymbolicLink(item.file.toPath())) { skipped++; continue }
                            scanned++
                            if (scanned > 100_000 || matches.size >= 5000) { limited = true; break }
                            if (!start.showSystemHidden && item.name.startsWith('.')) continue
                            if (!start.showManuallyHidden && item.path in start.manuallyHiddenPaths) continue
                            if (item.isDirectory && spec.recursive) {
                                if (depth < 100) queue.add(item.path to depth + 1) else { skipped++; limited = true }
                            }
                            var accepted = spec.matches(item.name, item.isDirectory, item.fileSize)
                            if (accepted && spec.content.isNotEmpty()) {
                                if (item.fileSize > 10L * 1024 * 1024) { accepted = false; skipped++ }
                                else {
                                    var downloaded: File? = null
                                    try {
                                        val input = when {
                                            item.isFtp -> { downloaded = network.materialize(item) { coroutine.ensureActive() }; downloaded!!.inputStream() }
                                            item.isSaf -> app.contentResolver.openInputStream(Uri.parse(item.path)) ?: throw IOException("Dokument kann nicht gelesen werden")
                                            else -> item.file.inputStream()
                                        }
                                        accepted = input.bufferedReader(Charsets.UTF_8).use { spec.contains(it) { coroutine.ensureActive() } }
                                    } catch (error: IOException) { accepted = false; skipped++ }
                                    finally { downloaded?.parentFile?.deleteTreeSafely() }
                                }
                            }
                            if (accepted) { matches.add(item); parents[item.path] = directory }
                            if (scanned % 50 == 0) ToolTaskRegistry.progress(task, message = "$scanned geprüft · ${matches.size} Treffer · $skipped übersprungen")
                        }
                        if (scanned > 100_000 || matches.size >= 5000) break
                    }
                    coroutine.ensureActive()
                    if (loadGeneration.getValue(pane).get() == generation) {
                        searchParents[pane] = parents
                        val label = "${matches.size} Treffer · $scanned geprüft" + (if (skipped > 0) " · $skipped übersprungen" else "") + (if (limited) " · Limit erreicht" else "")
                        updatePaneState(pane) { it.copy(items = matches, isLoading = false, loadingLabel = null, loadingProgress = null,
                            searchQuery = "", filter = FileFilter.ALL, searchResultsLabel = label, highlightedItemName = null, highlightedItemPath = null, recentlyChangedPaths = emptySet()) }
                        ToolTaskRegistry.progress(task, message = label)
                    }
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { _operationMessages.tryEmit("Suche fehlgeschlagen: ${error.message}") }
            finally {
                if (loadGeneration.getValue(pane).get() == generation) updatePaneState(pane) { it.copy(isLoading = false, loadingLabel = null, loadingProgress = null) }
            }
        }
        searchJobs[pane] = job
        job.invokeOnCompletion { searchJobs.remove(pane, job) }
    }

    suspend fun previewRename(pane: ActivePane, items: List<FileItem>, spec: FileWorkflow.RenameSpec): RenamePreview = withContext(Dispatchers.IO) {
        if (_fileTransfer.value.running || mutationRunning.get()) throw IOException("Datei-Aktion zuerst beenden")
        val state = paneState(pane)
        fun hierarchy(item: FileItem): String = when {
            item.isSaf -> android.provider.DocumentsContract.getDocumentId(Uri.parse(item.path))
            item.isFtp -> FtpLocation.session(item.path) + FtpLocation.remotePath(item.path)
            else -> item.file.absolutePath
        }
        items.filter { it.isDirectory }.forEach { directory ->
            val prefix = hierarchy(directory).trimEnd('/') + "/"
            if (items.any { it.path != directory.path && hierarchy(it).startsWith(prefix) })
                throw IOException("Ordner und enthaltene Dateien getrennt umbenennen")
        }
        val parents = items.associate { item -> item.path to when {
            item.isFtp -> FtpLocation.parent(item.path)
            item.isSaf -> searchParents[pane]?.get(item.path) ?: state.currentPath
            else -> item.file.parent ?: throw IOException("Datei hat keinen Elternordner")
        } }
        val proposed = items.mapIndexed { index, item -> FileWorkflow.RenameEntry(item.path, item.name, spec.name(item.name, index, items.size)) }
        val resolved = proposed.groupBy { parents.getValue(it.id) }.flatMap { (parent, entries) ->
            val names = listFilesAt(parent).map { it.name }.toSet()
            if (entries.any { it.original !in names }) throw IOException("Ausgewählte Datei wurde inzwischen geändert")
            FileWorkflow.resolve(entries, names)
        }.associateBy { it.id }
        RenamePreview(pane, state.currentPath, proposed.map { resolved.getValue(it.id) }, parents)
    }

    fun renameMultiple(preview: RenamePreview) {
        if (_fileTransfer.value.running || _archiveTasks.value.any { !it.isTerminal } || !mutationRunning.compareAndSet(false, true)) {
            _operationMessages.tryEmit("Datei- oder Archiv-Aktion zuerst beenden"); return
        }
        viewModelScope.launch(Dispatchers.IO) {
            val task = ToolTaskRegistry.begin("Mehrfach-Umbenennen", "${preview.entries.size} Dateien")
            var completed = 0
            try {
                val grouped = preview.entries.groupBy { preview.parents.getValue(it.id) }
                for ((parent, entries) in grouped) {
                    val handles = listFilesAt(parent).associate { it.name to it.path }.toMutableMap()
                    val storage = object : FileWorkflow.RenameStorage {
                        override fun same(source: String, target: String): Boolean = !FtpLocation.isRemote(parent) && !SafFileSystem.isSafPath(parent) &&
                            runCatching { Files.isSameFile(File(parent, source).toPath(), File(parent, target).toPath()) }.getOrDefault(false)
                        override fun exists(name: String): Boolean = when {
                            FtpLocation.isRemote(parent) -> network.connection(parent).client.stat(io.github.lootdev78.mtftp.FtpPaths.child(FtpLocation.remotePath(parent), name)) != null
                            SafFileSystem.isSafPath(parent) -> listFilesAt(parent).any { it.name == name }
                            else -> Files.exists(File(parent, name).toPath(), java.nio.file.LinkOption.NOFOLLOW_LINKS)
                        }
                        override fun move(source: String, target: String) {
                            if (exists(target)) throw IOException("Ziel existiert inzwischen: $target")
                            val sourcePath = handles[source] ?: throw IOException("Quelle fehlt: $source")
                            val targetPath = when {
                                FtpLocation.isRemote(parent) -> { network.rename(sourcePath, target); FtpLocation.child(parent, target) }
                                SafFileSystem.isSafPath(parent) -> {
                                    val renamed = SafFileSystem.rename(app, Uri.parse(sourcePath), target)
                                    val actual = SafFileSystem.documentName(app, renamed)
                                    if (actual != target) {
                                        runCatching { SafFileSystem.rename(app, renamed, source) }
                                        throw IOException("Dokumentanbieter erlaubt diesen Namen nicht: $target")
                                    }
                                    renamed.toString()
                                }
                                else -> { val destination = File(parent, target); Files.move(File(sourcePath).toPath(), destination.toPath()); destination.absolutePath }
                            }
                            handles.remove(source); handles[target] = targetPath
                        }
                    }
                    FileWorkflow.rename(entries, storage) { ToolTaskRegistry.progress(task, message = "Umbenennen in $parent") }
                    completed += entries.count { !it.unchanged() }
                    if (paneState(preview.pane).isArchiveView) scheduleArchiveCommit(preview.pane)
                }
                clearSelection(preview.pane)
                ToolTaskRegistry.finish(task, message = "$completed Dateien umbenannt")
                _operationMessages.tryEmit("$completed Dateien umbenannt")
            } catch (error: Exception) {
                val rollback = if (error.suppressed.isNotEmpty()) " · Rücksetzen unvollständig; temporäre Dateien prüfen" else ""
                val message = "Umbenennen fehlgeschlagen: ${error.message}$rollback · $completed zuvor abgeschlossen"
                ToolTaskRegistry.finish(task, ToolTaskStatus.FAILED, message); _operationMessages.tryEmit(message)
            } finally {
                mutationRunning.set(false)
                refreshMountedArchiveStates()
                ActivePane.entries.forEach(::refreshDirectory)
            }
        }
    }

    fun clearSearch(pane: ActivePane) {
        updatePaneState(pane) { it.copy(searchQuery = "") }
    }

    fun setShowSystemHidden(pane: ActivePane, show: Boolean) {
        panePreferences.edit().putBoolean("show_system_hidden", show).apply()
        ActivePane.entries.forEach { updatePaneState(it) { state -> state.copy(showSystemHidden = show) } }
    }

    fun setShowManuallyHidden(pane: ActivePane, show: Boolean) {
        panePreferences.edit().putBoolean("show_manual_hidden", show).apply()
        ActivePane.entries.forEach { updatePaneState(it) { state -> state.copy(showManuallyHidden = show) } }
    }

    fun hideSelectedManually(pane: ActivePane) {
        val selected = paneState(pane).selectedPaths
        if (selected.isEmpty()) return
        manualHiddenPaths = manualHiddenPaths + selected
        syncManualHiddenPaths()
        clearSelection(pane)
        _operationMessages.tryEmit("${selected.size} item(s) hidden")
    }

    fun unhideManualPath(path: String) {
        manualHiddenPaths = manualHiddenPaths - path
        syncManualHiddenPaths()
    }

    fun clearManualHidden() {
        manualHiddenPaths = emptySet()
        syncManualHiddenPaths()
    }

    fun manualHiddenPaths(): List<String> = manualHiddenPaths.sorted()

    fun setSort(pane: ActivePane, spec: SortSpec, onlyThisFolder: Boolean) {
        val state = paneState(pane)
        if (onlyThisFolder) {
            folderSortOverrides[sortKey(pane, state.currentPath)] = spec
        } else {
            if (pane == ActivePane.LEFT) leftDefaultSort = spec else rightDefaultSort = spec
            folderSortOverrides.remove(sortKey(pane, state.currentPath))
        }
        updatePaneState(pane) { it.copy(sortSpec = spec) }
        saveSortPreferences()
    }

    fun clearFolderSortOverrides(pane: ActivePane) {
        val prefix = pane.name + ":"
        folderSortOverrides.keys.filter { it.startsWith(prefix) }.toList().forEach(folderSortOverrides::remove)
        updatePaneState(pane) { it.copy(sortSpec = defaultSort(pane)) }
        saveSortPreferences()
    }

    fun setFilter(pane: ActivePane, filter: FileFilter) {
        updatePaneState(pane) { it.copy(filter = filter, selectedPaths = emptySet()) }
    }

    private fun syncManualHiddenPaths() {
        panePreferences.edit().putStringSet("manually_hidden_files", manualHiddenPaths.toSet()).apply()
        _leftPaneState.update { it.copy(manuallyHiddenPaths = manualHiddenPaths) }
        _rightPaneState.update { it.copy(manuallyHiddenPaths = manualHiddenPaths) }
    }

    private fun paneState(pane: ActivePane): PaneState =
        if (pane == ActivePane.LEFT) _leftPaneState.value else _rightPaneState.value

    private fun saveSortPreferences() {
        fun value(spec: SortSpec) = org.json.JSONObject().put("field", spec.field.name).put("descending", spec.descending)
        val overrides = org.json.JSONObject()
        folderSortOverrides.filterKeys { !it.contains("mtapktool-archive-workspaces") && !it.contains("mtftp://") }.forEach { (key, spec) -> overrides.put(key, value(spec)) }
        panePreferences.edit().putString("sort_LEFT", value(leftDefaultSort).toString()).putString("sort_RIGHT", value(rightDefaultSort).toString()).putString("folder_sorts", overrides.toString()).apply()
    }

    private fun defaultSort(pane: ActivePane): SortSpec =
        if (pane == ActivePane.LEFT) leftDefaultSort else rightDefaultSort

    private fun sortKey(pane: ActivePane, path: String): String = "${pane.name}:$path"

    // --- Direct Path Navigation with Scroll/Highlight Target ---
    fun revealOutput(pane: ActivePane, outputPath: String) {
        if (SafFileSystem.isSafPath(outputPath)) { loadDirectory(pane, outputPath); return }
        val output = File(outputPath)
        val directory = output.parentFile ?: return
        highlightDocument(pane, directory.absolutePath, output.name)
    }

    fun navigateToDirectPath(pane: ActivePane, fullPath: String) {
        val text = fullPath.trim()
        if (FtpLocation.isRemote(text)) { loadDirectory(pane, text); return }
        if (SafFileSystem.isSafPath(text)) {
            loadDirectory(pane, text)
            return
        }
        val target = File(text)
        if (!target.exists()) {
            _operationMessages.tryEmit("Path not found: ${target.path}")
            return
        }

        val directoryPath = if (target.isDirectory) target.absolutePath else target.parent ?: run {
            _operationMessages.tryEmit("Cannot open path: ${target.path}")
            return
        }
        if (target.isDirectory) loadDirectory(pane, directoryPath)
        else highlightDocument(pane, directoryPath, target.name)
    }

    override fun onCleared() {
        transferJob?.cancel()
        pendingConflict?.complete(FileConflictAction.CANCEL)
        network.close()
        archiveSessionManager.discardAll()
        archiveSessionIds.clear()
        pendingArchiveOpen.clear()
        super.onCleared()
    }


    private fun preserveTimestamps(source: File, destination: File) {
        if (source.isDirectory && destination.isDirectory) {
            source.listFiles()?.forEach { child ->
                val target = File(destination, child.name)
                if (target.exists()) preserveTimestamps(child, target)
            }
        }
        destination.setLastModified(source.lastModified())
    }

    fun recycleBinPath(): String = File(ExplorerPreferences.current(app).customWorkspace, ".RecycleBin").absolutePath

    fun openRecycleBin(pane: ActivePane) {
        val prefs = ExplorerPreferences.current(app)
        if (!prefs.recycleBinEnabled) {
            _operationMessages.tryEmit("Recycle bin is disabled")
            return
        }
        val root = File(prefs.customWorkspace, ".RecycleBin").apply { mkdirs() }
        setActive(pane)
        loadDirectory(pane, root.absolutePath)
    }

    fun emptyRecycleBin(pane: ActivePane) {
        val root = File(ExplorerPreferences.current(app).customWorkspace, ".RecycleBin")
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                root.listFiles()?.forEach { if (it.isDirectory) it.deleteTreeSafely() else it.delete() }
            }.onSuccess { refreshDirectory(pane); _operationMessages.tryEmit("Recycle bin emptied") }
                .onFailure { _operationMessages.tryEmit("Could not empty recycle bin: ${it.message}") }
        }
    }

    private fun cleanRecycleBinIfNeeded(workspace: String, days: Int) {
        if (days <= 0) return
        viewModelScope.launch(Dispatchers.IO) {
            val root = File(workspace, ".RecycleBin")
            if (!root.isDirectory) return@launch
            val cutoff = System.currentTimeMillis() - days * 24L * 60L * 60L * 1000L
            root.listFiles()?.filter { it.lastModified() in 1 until cutoff }?.forEach { file ->
                if (file.isDirectory) file.deleteTreeSafely() else file.delete()
            }
        }
    }

    companion object {
        private const val RECENT_HIGHLIGHT_MS = 120_000L
    }
}
