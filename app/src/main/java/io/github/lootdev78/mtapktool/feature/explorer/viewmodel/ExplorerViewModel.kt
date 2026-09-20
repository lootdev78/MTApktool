package io.github.lootdev78.mtapktool.feature.explorer.viewmodel

import android.app.Application
import android.net.Uri
import android.os.Environment
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
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
import io.github.lootdev78.mtapktool.feature.explorer.model.FileItem
import io.github.lootdev78.mtapktool.feature.explorer.saf.SafFileSystem
import io.github.lootdev78.mtapktool.feature.explorer.state.FileFilter
import io.github.lootdev78.mtapktool.feature.explorer.state.PaneState
import io.github.lootdev78.mtapktool.feature.explorer.state.SortSpec
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
    private val _archiveUpdateRequest = MutableStateFlow<ArchiveUpdateRequest?>(null)
    val archiveUpdateRequest: StateFlow<ArchiveUpdateRequest?> = _archiveUpdateRequest.asStateFlow()
    private val _archivePasswordRequest = MutableStateFlow<ArchivePasswordRequest?>(null)
    val archivePasswordRequest: StateFlow<ArchivePasswordRequest?> = _archivePasswordRequest.asStateFlow()
    private val archiveTaskId = AtomicLong(0L)
    private val archiveTaskJobs = ConcurrentHashMap<Long, Job>()
    private val _archiveTasks = MutableStateFlow<List<ArchiveTaskInfo>>(emptyList())
    val archiveTasks: StateFlow<List<ArchiveTaskInfo>> = _archiveTasks.asStateFlow()

    private fun beginArchiveTask(kind: ArchiveTaskKind, title: String, detail: String): Long {
        val id = archiveTaskId.incrementAndGet()
        val task = ArchiveTaskInfo(id, kind, title, detail, ArchiveTaskStatus.RUNNING, 0)
        _archiveTasks.update { listOf(task) + it }
        return id
    }

    private fun updateArchiveTask(id: Long, progress: Int? = null, message: String? = null) {
        _archiveTasks.update { tasks -> tasks.map { task -> if (task.id == id) task.copy(progress = progress ?: task.progress, message = message ?: task.message) else task } }
    }

    private fun finishArchiveTask(id: Long, success: Boolean, message: String = "") {
        _archiveTasks.update { tasks -> tasks.map { task ->
            if (task.id == id) task.copy(status = if (success) ArchiveTaskStatus.SUCCEEDED else ArchiveTaskStatus.FAILED, progress = if (success) 100 else task.progress, message = message)
            else task
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
        _archiveTasks.update { tasks -> tasks.map { task -> if (task.id == id) task.copy(status = ArchiveTaskStatus.CANCELLED, message = "Cancelled") else task } }
        viewModelScope.launch { delay(1000); _archiveTasks.update { tasks -> tasks.filterNot { it.id == id && it.isTerminal } } }
    }

    fun cancelAllArchiveTasks() = archiveTaskJobs.keys.toList().forEach(::cancelArchiveTask)

    private fun currentArchiveSession(pane: ActivePane): ArchiveSessionSnapshot? =
        archiveSessionIds[pane]?.let(archiveSessionManager::get)


    init {
        ExplorerPreferences.init(app)
        val prefs = ExplorerPreferences.current(app)
        val rootPath = Environment.getExternalStorageDirectory().absolutePath
        val leftStart = if (prefs.startupLeft == "last") panePreferences.getString("last_left_path", rootPath) ?: rootPath else rootPath
        val rightStart = if (prefs.startupRight == "last") panePreferences.getString("last_right_path", rootPath) ?: rootPath else rootPath
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

    fun loadDirectory(pane: ActivePane, path: String, isHistoryAction: Boolean = false) {
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
            val isSaf = SafFileSystem.isSafPath(path)
            val displayOverride = if (isSaf) {
                safDisplayByUri[path] ?: withContext(Dispatchers.IO) {
                    val name = SafFileSystem.documentName(app, Uri.parse(path)) ?: "Storage"
                    val parentDisplay = safDisplayByUri[previousState.currentPath]
                    val display = if (!isHistoryAction && parentDisplay != null && previousState.currentPath != path) "$parentDisplay/$name" else name
                    safDisplayByUri[path] = display
                    display
                }
            } else null

            updatePaneState(pane) { it.copy(isLoading = true, currentPath = path, displayPathOverride = displayOverride) }

            val result = withContext(Dispatchers.IO) {
                runCatching {
                    if (isSaf) SafFileSystem.list(app, Uri.parse(path))
                    else {
                        val dir = File(path)
                        if (dir.exists() && dir.isDirectory) dir.listFiles()?.map { FileItem(file = it) } ?: emptyList()
                        else emptyList()
                    }
                }
            }

            if (result.isFailure) {
                updatePaneState(pane) { it.copy(isLoading = false, loadingProgress = null, loadingLabel = null, items = emptyList()) }
                _operationMessages.tryEmit("Storage access failed: ${result.exceptionOrNull()?.message ?: "unknown error"}")
                return@launch
            }

            val files = result.getOrThrow()
            val sameDirectory = previousState.currentPath == path && previousState.items.isNotEmpty()
            val oldModified = previousState.items.associate { it.path to it.modifiedAt }
            val changedPaths = if (sameDirectory) {
                files.asSequence()
                    .filter { item -> oldModified[item.path]?.let { it != item.modifiedAt } ?: true }
                    .map { it.path }
                    .toSet()
            } else emptySet()
            val sort = folderSortOverrides[sortKey(pane, path)] ?: defaultSort(pane)

            updatePaneState(pane) { state ->
                state.copy(
                    items = files,
                    isLoading = false,
                    loadingProgress = null,
                    loadingLabel = null,
                    selectedPaths = emptySet(),
                    manuallyHiddenPaths = manualHiddenPaths,
                    sortSpec = sort,
                    recentlyChangedPaths = if (sameDirectory) state.recentlyChangedPaths + changedPaths else emptySet(),
                    displayPathOverride = displayOverride,
                )
            }

            panePreferences.edit()
                .putString(if (pane == ActivePane.LEFT) "last_left_path" else "last_right_path", path)
                .apply()

            if (changedPaths.isNotEmpty()) {
                viewModelScope.launch {
                    delay(RECENT_HIGHLIGHT_MS)
                    updatePaneState(pane) { it.copy(recentlyChangedPaths = it.recentlyChangedPaths - changedPaths) }
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
        val current = paneState(pane).currentPath
        if (SafFileSystem.isSafPath(current)) return current != safRootByPane[pane]
        if (currentArchiveSession(pane) != null) return true
        val rootPath = Environment.getExternalStorageDirectory().absolutePath
        return current != rootPath && current != "/" && File(current).parent != null
    }

    fun navigateUp(pane: ActivePane) {
        val current = paneState(pane).currentPath
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

        val rootPath = Environment.getExternalStorageDirectory().absolutePath
        if (current == rootPath || current == "/") return
        File(current).parent?.let { loadDirectory(pane, it) }
    }

    fun refreshDirectory(pane: ActivePane) {
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

    fun swapPanes() {
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

    fun copySelectedToOppositePane(fromPane: ActivePane) {
        val sourceState = paneState(fromPane)
        val targetPane = if (fromPane == ActivePane.LEFT) ActivePane.RIGHT else ActivePane.LEFT
        val targetPath = paneState(targetPane).currentPath
        val selected = sourceState.selectedPaths.mapNotNull { path -> sourceState.items.firstOrNull { it.path == path } }
        if (selected.isEmpty()) return

        beginTransferBatch()
        setPaneBusy(targetPane, true, "Kopieren …", 0)
        viewModelScope.launch(Dispatchers.IO) {
            val copiedNames = mutableListOf<String>()
            runCatching {
                selected.forEachIndexed { index, item ->
                    if (copyItemToTarget(item, targetPath)) copiedNames += item.name
                    setPaneBusy(targetPane, true, "Kopieren: ${item.name}", ((index + 1) * 100 / selected.size).coerceIn(0, 100))
                }
            }.onSuccess {
                setPaneBusy(targetPane, false)
                scheduleArchiveCommit(targetPane)
                refreshDirectory(targetPane)
                highlightTransferredItems(targetPane, copiedNames)
                clearSelection(fromPane)
                _operationMessages.tryEmit("Copied ${copiedNames.size} item(s)")
            }.onFailure { e ->
                setPaneBusy(targetPane, false)
                if (e is TransferCancelledException) {
                    _operationMessages.tryEmit("Copy cancelled")
                } else {
                    _operationMessages.tryEmit("Copy failed: ${e.message ?: e.javaClass.simpleName}")
                }
            }
        }
    }

    fun moveSelectedToOppositePane(fromPane: ActivePane) {
        val sourceState = paneState(fromPane)
        val targetPane = if (fromPane == ActivePane.LEFT) ActivePane.RIGHT else ActivePane.LEFT
        val targetPath = paneState(targetPane).currentPath
        val selected = sourceState.selectedPaths.mapNotNull { path -> sourceState.items.firstOrNull { it.path == path } }
        if (selected.isEmpty()) return

        beginTransferBatch()
        setPaneBusy(targetPane, true, "Verschieben …", 0)
        viewModelScope.launch(Dispatchers.IO) {
            val movedNames = mutableListOf<String>()
            runCatching {
                selected.forEachIndexed { index, item ->
                    if (!item.isSaf && !SafFileSystem.isSafPath(targetPath)) {
                        val source = File(item.path)
                        var destination = File(targetPath, item.name)
                        ensureTransferTargetIsSafe(source, destination)
                        if (destination.exists()) {
                            when (askConflict(item, destination.absolutePath)) {
                                FileConflictAction.SKIP -> return@forEach
                                FileConflictAction.CANCEL -> throw TransferCancelledException()
                                FileConflictAction.KEEP_BOTH -> destination = uniqueLocalTarget(destination)
                                FileConflictAction.OVERWRITE -> deleteExistingLocal(destination)
                            }
                        }
                        if (!source.renameTo(destination)) {
                            if (!copyLocalToLocal(source, destination)) throw IOException("Could not move ${source.name}")
                            if (source.isDirectory) {
                                if (!source.deleteRecursively()) throw IOException("Copied but could not remove ${source.name}")
                            } else if (!source.delete()) throw IOException("Copied but could not remove ${source.name}")
                        }
                        movedNames += destination.name
                    } else {
                        if (copyItemToTarget(item, targetPath)) {
                            deleteItem(item, recycleOverride = false)
                            movedNames += item.name
                        }
                    }
                    setPaneBusy(targetPane, true, "Verschieben: ${item.name}", ((index + 1) * 100 / selected.size).coerceIn(0, 100))
                }
            }.onSuccess {
                setPaneBusy(targetPane, false)
                scheduleArchiveCommit(fromPane)
                scheduleArchiveCommit(targetPane)
                refreshDirectory(fromPane)
                refreshDirectory(targetPane)
                highlightTransferredItems(targetPane, movedNames)
                clearSelection(fromPane)
                _operationMessages.tryEmit("Moved ${movedNames.size} item(s)")
            }.onFailure { e ->
                setPaneBusy(targetPane, false)
                if (e is TransferCancelledException) {
                    _operationMessages.tryEmit("Move cancelled")
                } else {
                    _operationMessages.tryEmit("Move failed: ${e.message ?: e.javaClass.simpleName}")
                }
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
                    archiveSessionManager.discard(current.id)
                    archiveSessionIds.remove(pane)
                }
                val parentId = if (nested) current?.id else null
                val returnDir = archive.parentFile?.absolutePath ?: Environment.getExternalStorageDirectory().absolutePath
                val session = archiveSessionManager.open(archive, returnDir, password, parentId) { progress ->
                    this@launch.ensureActive()
                    updateArchiveTask(taskId, progress, "Opening ${archive.name}")
                    updatePaneState(pane) { state -> state.copy(isLoading = true, loadingProgress = progress.coerceIn(0, 100), loadingLabel = "Öffne ${archive.name}") }
                }
                archiveSessionIds[pane] = session.id
                updatePaneState(pane) { it.copy(archiveFilePath = session.archive.absolutePath, archiveRootPath = session.workspaceRoot.absolutePath, highlightedItemName = null) }
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
                } else {
                    if (isArchivePasswordFailure(error)) {
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
        }
        archiveTaskJobs[taskId] = job
    }

    /** Explicitly commits every dirty mounted archive; never called automatically on lifecycle changes. */
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
        ActivePane.entries.forEach { pane -> archiveSessionIds[pane]?.let(archiveSessionManager::refresh) }
    }

    fun requestCloseArchive(pane: ActivePane) {
        val session = currentArchiveSession(pane) ?: return
        val refreshed = archiveSessionManager.refresh(session.id) ?: session
        if (refreshed.state == ArchiveSessionState.DIRTY || refreshed.state == ArchiveSessionState.FAILED) {
            _archiveUpdateRequest.value = ArchiveUpdateRequest(
                pane = pane,
                archiveName = refreshed.archive.name,
                sessionState = refreshed.state,
                dirtyEntries = refreshed.dirtyEntries,
                nestedDepth = refreshed.depth,
            )
        } else {
            closeArchive(pane, saveChanges = false)
        }
    }

    fun resolveArchiveUpdate(decision: ArchiveUpdateDecision) {
        val request = _archiveUpdateRequest.value ?: return
        if (decision == ArchiveUpdateDecision.CANCEL) {
            pendingArchiveOpen.remove(request.pane)
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
            finishArchiveClose(pane, session, committed = saveChanges)
        }
    }

    private fun finishArchiveClose(pane: ActivePane, session: ArchiveSessionSnapshot, committed: Boolean) {
        val parentId = session.parentId
        if (committed) archiveSessionManager.closeAfterCommit(session.id) else archiveSessionManager.discard(session.id)
        if (parentId != null) {
            val parent = archiveSessionManager.get(parentId)
            if (parent != null) {
                archiveSessionIds[pane] = parent.id
                updatePaneState(pane) {
                    it.copy(
                        archiveFilePath = parent.archive.absolutePath,
                        archiveRootPath = parent.workspaceRoot.absolutePath,
                        highlightedItemName = session.archive.name,
                    )
                }
                val target = session.archive.parentFile?.takeIf { isInside(it, parent.workspaceRoot) } ?: parent.workspaceRoot
                loadDirectory(pane, target.absolutePath, isHistoryAction = true)
                return
            }
        }
        archiveSessionIds.remove(pane)
        updatePaneState(pane) {
            it.copy(archiveFilePath = null, archiveRootPath = null, highlightedItemName = session.archive.name)
        }
        loadDirectory(pane, session.returnDirectory, isHistoryAction = true)
    }

    private fun resumePendingArchiveOpen(pane: ActivePane) {
        val pending = pendingArchiveOpen.remove(pane) ?: return
        openArchive(pane, pending.archive, pending.password)
    }

    fun navigateToDisplayPath(pane: ActivePane, value: String) {
        val text = value.trim()
        val session = currentArchiveSession(pane)
        if (session != null) {
            val prefix = session.archive.absolutePath + "!/"
            if (text == session.archive.absolutePath + "!" || text == prefix || text.startsWith(prefix)) {
                val relative = text.removePrefix(prefix).trimStart('/', '\\')
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
        navigateToDirectPath(pane, text)
    }

    private fun scheduleArchiveCommit(pane: ActivePane) {
        val session = currentArchiveSession(pane) ?: return
        val relative = paneState(pane).currentPath.takeIf { isInside(File(it), session.workspaceRoot) }
            ?.let { runCatching { File(it).relativeTo(session.workspaceRoot).invariantSeparatorsPath }.getOrNull() }
        archiveSessionManager.markDirty(session.id, relative)
    }

    private fun markArchivesAffectedBy(vararg files: File) {
        ActivePane.entries.forEach { pane ->
            val session = currentArchiveSession(pane) ?: return@forEach
            if (files.any { isInside(it, session.workspaceRoot) }) archiveSessionManager.markDirty(session.id)
        }
    }

    private fun isInside(file: File, root: File): Boolean = runCatching {
        val candidate = file.canonicalFile
        val base = root.canonicalFile
        candidate == base || candidate.path.startsWith(base.path + File.separator)
    }.getOrDefault(false)

    private fun sameFile(first: File, second: File): Boolean = runCatching {
        first.canonicalFile == second.canonicalFile
    }.getOrDefault(first.absolutePath == second.absolutePath)

    private fun highlightTransferredItems(pane: ActivePane, names: List<String>) {
        if (names.isEmpty()) return
        viewModelScope.launch {
            repeat(20) {
                delay(75)
                val matches = paneState(pane).items.filter { it.name in names }
                if (matches.isNotEmpty()) {
                    val paths = matches.map { it.path }.toSet()
                    updatePaneState(pane) { state ->
                        state.copy(
                            highlightedItemName = matches.first().name,
                            recentlyChangedPaths = state.recentlyChangedPaths + paths,
                        )
                    }
                    delay(RECENT_HIGHLIGHT_MS)
                    updatePaneState(pane) { state ->
                        state.copy(
                            highlightedItemName = if (state.highlightedItemName in names) null else state.highlightedItemName,
                            recentlyChangedPaths = state.recentlyChangedPaths - paths,
                        )
                    }
                    return@launch
                }
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
        val ok = if (file.isDirectory) file.deleteRecursively() else file.delete()
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
        val externalRoot = Environment.getExternalStorageDirectory().absolutePath
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
                if (file.isDirectory) {
                    if (!file.copyRecursively(target, overwrite = false)) throw IOException("Could not move ${item.name} to recycle bin")
                    if (!file.deleteRecursively()) throw IOException("Could not remove ${item.name} after recycling")
                } else {
                    file.copyTo(target, overwrite = false)
                    if (!file.delete()) throw IOException("Could not remove ${item.name} after recycling")
                }
            }
            return
        }
        val ok = if (file.isDirectory) file.deleteRecursively() else file.delete()
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
            runCatching { items.forEach { deleteItem(it, recycleOverride) } }
                .onSuccess {
                    scheduleArchiveCommit(pane)
                    refreshDirectory(pane)
                    clearSelection(pane)
                }
                .onFailure { _operationMessages.tryEmit("Delete failed: ${it.message ?: it.javaClass.simpleName}") }
        }
    }

    // --- Create New File or Folder ---
    fun createNewItem(pane: ActivePane, name: String, isFolder: Boolean): String? {
        val currentPath = paneState(pane).currentPath
        if (name.isBlank()) return "Name is empty"
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
            runCatching {
                if (SafFileSystem.isSafPath(oldPath)) {
                    SafFileSystem.rename(app, Uri.parse(oldPath), newName)
                } else {
                    val oldFile = File(oldPath)
                    if (!oldFile.exists()) throw IOException("Source no longer exists")
                    val newFile = File(oldFile.parent, newName)
                    if (!oldFile.renameTo(newFile)) throw IOException("Rename failed")
                    scheduleArchiveCommit(pane)
                }
            }.onSuccess { refreshDirectory(pane) }
                .onFailure { _operationMessages.tryEmit("Rename failed: ${it.message ?: it.javaClass.simpleName}") }
        }
    }

    fun setSearchQuery(pane: ActivePane, query: String) {
        updatePaneState(pane) { it.copy(searchQuery = query) }
    }

    fun clearSearch(pane: ActivePane) {
        updatePaneState(pane) { it.copy(searchQuery = "") }
    }

    fun setShowSystemHidden(pane: ActivePane, show: Boolean) {
        updatePaneState(pane) { it.copy(showSystemHidden = show) }
    }

    fun setShowManuallyHidden(pane: ActivePane, show: Boolean) {
        updatePaneState(pane) { it.copy(showManuallyHidden = show) }
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
    }

    fun clearFolderSortOverrides(pane: ActivePane) {
        val prefix = pane.name + ":"
        folderSortOverrides.keys.filter { it.startsWith(prefix) }.toList().forEach(folderSortOverrides::remove)
        updatePaneState(pane) { it.copy(sortSpec = defaultSort(pane)) }
    }

    fun setFilter(pane: ActivePane, filter: FileFilter) {
        updatePaneState(pane) { it.copy(filter = filter, selectedPaths = emptySet()) }
    }

    private fun syncManualHiddenPaths() {
        _leftPaneState.update { it.copy(manuallyHiddenPaths = manualHiddenPaths) }
        _rightPaneState.update { it.copy(manuallyHiddenPaths = manualHiddenPaths) }
    }

    private fun paneState(pane: ActivePane): PaneState =
        if (pane == ActivePane.LEFT) _leftPaneState.value else _rightPaneState.value

    private fun defaultSort(pane: ActivePane): SortSpec =
        if (pane == ActivePane.LEFT) leftDefaultSort else rightDefaultSort

    private fun sortKey(pane: ActivePane, path: String): String = "${pane.name}:$path"

    // --- Direct Path Navigation with Scroll/Highlight Target ---
    fun revealOutput(pane: ActivePane, outputPath: String) {
        if (SafFileSystem.isSafPath(outputPath)) {
            loadDirectory(pane, outputPath)
            return
        }
        val output = File(outputPath)
        val directory = if (output.isDirectory) output.parentFile else output.parentFile
        val targetName = output.name
        if (directory == null) return
        loadDirectory(pane, directory.absolutePath)
        viewModelScope.launch {
            var waitCount = 0
            while (waitCount < 60) {
                val state = paneState(pane)
                if (!state.isLoading && state.currentPath == directory.absolutePath) break
                delay(50)
                waitCount++
            }
            val full = File(directory, targetName).absolutePath
            updatePaneState(pane) { state ->
                state.copy(
                    highlightedItemName = targetName,
                    recentlyChangedPaths = state.recentlyChangedPaths + full,
                )
            }
            delay(RECENT_HIGHLIGHT_MS)
            updatePaneState(pane) { state ->
                state.copy(
                    highlightedItemName = if (state.highlightedItemName == targetName) null else state.highlightedItemName,
                    recentlyChangedPaths = state.recentlyChangedPaths - full,
                )
            }
        }
    }

    fun navigateToDirectPath(pane: ActivePane, fullPath: String) {
        val text = fullPath.trim()
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
        val highlightFileName = if (target.isDirectory) null else target.name
        loadDirectory(pane, directoryPath)
        if (highlightFileName != null) {
            updatePaneState(pane) { it.copy(highlightedItemName = highlightFileName) }
            viewModelScope.launch {
                delay(RECENT_HIGHLIGHT_MS)
                updatePaneState(pane) { if (it.highlightedItemName == highlightFileName) it.copy(highlightedItemName = null) else it }
            }
        }
    }
    override fun onCleared() {
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
                root.listFiles()?.forEach { if (it.isDirectory) it.deleteRecursively() else it.delete() }
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
                if (file.isDirectory) file.deleteRecursively() else file.delete()
            }
        }
    }

    companion object {
        private const val RECENT_HIGHLIGHT_MS = 120_000L
    }
}
