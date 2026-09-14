package io.github.lootdev78.mtapktool.feature.explorer.viewmodel

import android.app.Application
import android.os.Environment
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.lootdev78.mtapktool.archive.ArchiveEngine
import io.github.lootdev78.mtapktool.archive.ArchiveExtractRequest
import io.github.lootdev78.mtapktool.archive.ArchiveRequest
import io.github.lootdev78.mtapktool.feature.explorer.model.FileItem
import io.github.lootdev78.mtapktool.feature.explorer.state.FileFilter
import io.github.lootdev78.mtapktool.feature.explorer.state.PaneState
import io.github.lootdev78.mtapktool.feature.explorer.state.SortSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.util.Stack
import java.util.concurrent.ConcurrentHashMap

enum class ActivePane { LEFT, RIGHT }

class ExplorerViewModel(application: Application) : AndroidViewModel(application) {

    private val _leftPaneState = MutableStateFlow(PaneState())
    val leftPaneState: StateFlow<PaneState> = _leftPaneState.asStateFlow()

    private val _rightPaneState = MutableStateFlow(PaneState())
    val rightPaneState: StateFlow<PaneState> = _rightPaneState.asStateFlow()


    private val _activePane = MutableStateFlow(ActivePane.LEFT)
    val activePane: StateFlow<ActivePane> = _activePane.asStateFlow()

    private val _operationMessages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val operationMessages: SharedFlow<String> = _operationMessages.asSharedFlow()

    // Separate Back/Forward stacks for dual pane navigation history
    private val leftBackStack = Stack<String>()
    private val leftForwardStack = Stack<String>()

    private val rightBackStack = Stack<String>()
    private val rightForwardStack = Stack<String>()

    private var leftDefaultSort = SortSpec()
    private var rightDefaultSort = SortSpec()
    private val folderSortOverrides = mutableMapOf<String, SortSpec>()
    private var manualHiddenPaths: Set<String> = emptySet()

    private data class ArchiveSession(
        val archive: File,
        val workspaceRoot: File,
        val returnDirectory: String,
        val password: String,
        var snapshot: Map<String, ArchiveStamp>,
    )

    private data class ArchiveStamp(
        val directory: Boolean,
        val size: Long,
        val modifiedAt: Long,
    )

    private val archiveSessions = ConcurrentHashMap<ActivePane, ArchiveSession>()

    init {
        val rootPath = Environment.getExternalStorageDirectory().absolutePath
        loadDirectory(
            ActivePane.LEFT,
            rootPath,
            isHistoryAction = false
        )

        loadDirectory(
            ActivePane.RIGHT,
            rootPath,
            isHistoryAction = false
        )
    }

    fun setActive(pane: ActivePane) {
        _activePane.value = pane
    }

    fun restoreExplorerOptions(
        leftShowSystemHidden: Boolean,
        rightShowSystemHidden: Boolean,
        leftShowManuallyHidden: Boolean,
        rightShowManuallyHidden: Boolean,
        hiddenPaths: Set<String>,
        leftSort: SortSpec,
        rightSort: SortSpec,
        leftFilter: FileFilter,
        rightFilter: FileFilter,
        sortOverrides: Map<String, SortSpec>,
    ) {
        manualHiddenPaths = hiddenPaths
        leftDefaultSort = leftSort
        rightDefaultSort = rightSort
        folderSortOverrides.clear()
        folderSortOverrides.putAll(sortOverrides)
        _leftPaneState.update { state ->
            state.copy(
                showSystemHidden = leftShowSystemHidden,
                showManuallyHidden = leftShowManuallyHidden,
                manuallyHiddenPaths = hiddenPaths,
                sortSpec = folderSortOverrides[sortKey(ActivePane.LEFT, state.currentPath)] ?: leftSort,
                filter = leftFilter,
            )
        }
        _rightPaneState.update { state ->
            state.copy(
                showSystemHidden = rightShowSystemHidden,
                showManuallyHidden = rightShowManuallyHidden,
                manuallyHiddenPaths = hiddenPaths,
                sortSpec = folderSortOverrides[sortKey(ActivePane.RIGHT, state.currentPath)] ?: rightSort,
                filter = rightFilter,
            )
        }
    }

    fun folderSortOverridesSnapshot(): Map<String, SortSpec> = folderSortOverrides.toMap()

    fun loadDirectory(pane: ActivePane, path: String, isHistoryAction: Boolean = false) {
        val previousState = paneState(pane)
        val currentPath = previousState.currentPath

        // If navigating to a new path (not back/forward action), save to back stack & clear forward stack.
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
            updatePaneState(pane) { it.copy(isLoading = true, currentPath = path) }

            val files = withContext(Dispatchers.IO) {
                val dir = File(path)
                if (dir.exists() && dir.isDirectory) {
                    dir.listFiles()?.map { FileItem(file = it) } ?: emptyList()
                } else {
                    emptyList()
                }
            }

            val sameDirectory = previousState.currentPath == path && previousState.items.isNotEmpty()
            val oldModified = previousState.items.associate { it.path to it.modifiedAt }
            val changedPaths = if (sameDirectory) {
                files.asSequence()
                    .filter { item -> oldModified[item.path]?.let { it != item.modifiedAt } ?: true }
                    .map { it.path }
                    .toSet()
            } else {
                emptySet()
            }
            val sort = folderSortOverrides[sortKey(pane, path)] ?: defaultSort(pane)

            updatePaneState(pane) { state ->
                state.copy(
                    items = files,
                    isLoading = false,
                    selectedPaths = emptySet(),
                    manuallyHiddenPaths = manualHiddenPaths,
                    sortSpec = sort,
                    recentlyChangedPaths = if (sameDirectory) state.recentlyChangedPaths + changedPaths else emptySet(),
                )
            }

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
        if (archiveSessions.containsKey(pane)) {
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
        if (archiveSessions.containsKey(pane)) return
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

    fun navigateUp(pane: ActivePane) {
        val current = paneState(pane).currentPath
        archiveSessions[pane]?.let { session ->
            val currentFile = File(current)
            val root = session.workspaceRoot
            if (sameFile(currentFile, root)) {
                closeArchive(pane, saveChanges = true)
                return
            }
            val parent = currentFile.parentFile
            if (parent != null && isInside(parent, root)) {
                loadDirectory(pane, parent.absolutePath)
                return
            }
            closeArchive(pane, saveChanges = true)
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
        archiveSessions[sourcePane]?.let { session ->
            openArchive(targetPane, session.archive, session.password)
            return
        }
        loadDirectory(pane = targetPane, path = paneState(sourcePane).currentPath)
    }

    fun copySelectedToOppositePane(fromPane: ActivePane) {
        val sourceState = if (fromPane == ActivePane.LEFT) _leftPaneState.value else _rightPaneState.value
        val targetPane = if (fromPane == ActivePane.LEFT) ActivePane.RIGHT else ActivePane.LEFT
        if (archiveSessions.containsKey(targetPane)) {
            _operationMessages.tryEmit("Archive are read-only. Copy files out to a normal folder instead.")
            return
        }
        val targetPath = if (targetPane == ActivePane.RIGHT) _rightPaneState.value.currentPath else _leftPaneState.value.currentPath

        val itemsToCopy = sourceState.selectedPaths
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                itemsToCopy.forEach { sourcePath ->
                    val sourceFile = File(sourcePath)
                    if (sourceFile.exists()) {
                        val destFile = File(targetPath, sourceFile.name)
                        ensureTransferTargetIsSafe(sourceFile, destFile)
                        if (sourceFile.isDirectory) {
                            if (!sourceFile.copyRecursively(destFile, overwrite = true)) {
                                throw IOException("Could not copy directory: ${sourceFile.path}")
                            }
                        } else {
                            sourceFile.copyTo(destFile, overwrite = true)
                        }
                    }
                }
            }.onSuccess {
                scheduleArchiveCommit(targetPane)
                refreshDirectory(targetPane)
                clearSelection(fromPane)
            }.onFailure { e ->
                _operationMessages.tryEmit("Copy failed: ${e.message ?: e.javaClass.simpleName}")
                e.printStackTrace()
            }
        }
    }

    fun moveSelectedToOppositePane(fromPane: ActivePane) {
        val sourceState = if (fromPane == ActivePane.LEFT) _leftPaneState.value else _rightPaneState.value
        val targetPane = if (fromPane == ActivePane.LEFT) ActivePane.RIGHT else ActivePane.LEFT
        if (archiveSessions.containsKey(fromPane) || archiveSessions.containsKey(targetPane)) {
            _operationMessages.tryEmit("Archive are read-only. Use Copy to extract files from an archive.")
            return
        }
        val targetPath = if (targetPane == ActivePane.RIGHT) _rightPaneState.value.currentPath else _leftPaneState.value.currentPath

        val itemsToMove = sourceState.selectedPaths
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                itemsToMove.forEach { sourcePath ->
                    val sourceFile = File(sourcePath)
                    if (!sourceFile.exists()) return@forEach
                    val destFile = File(targetPath, sourceFile.name)
                    ensureTransferTargetIsSafe(sourceFile, destFile)
                    if (!sourceFile.renameTo(destFile)) {
                        if (sourceFile.isDirectory) {
                            if (!sourceFile.copyRecursively(destFile, overwrite = true)) {
                                throw IOException("Could not copy directory while moving: ${sourceFile.path}")
                            }
                            if (!sourceFile.deleteRecursively()) {
                                throw IOException("Copied but could not remove source directory: ${sourceFile.path}")
                            }
                        } else {
                            sourceFile.copyTo(destFile, overwrite = true)
                            if (!sourceFile.delete()) {
                                throw IOException("Copied but could not remove source file: ${sourceFile.path}")
                            }
                        }
                    }
                }
            }.onSuccess {
                scheduleArchiveCommit(fromPane)
                scheduleArchiveCommit(targetPane)
                refreshDirectory(fromPane)
                refreshDirectory(targetPane)
                clearSelection(fromPane)
            }.onFailure { e ->
                _operationMessages.tryEmit("Move failed: ${e.message ?: e.javaClass.simpleName}")
                e.printStackTrace()
            }
        }
    }

    fun linkToOppositePane(fromPane: ActivePane, sourcePath: String) {
        val targetPane = if (fromPane == ActivePane.LEFT) ActivePane.RIGHT else ActivePane.LEFT
        if (archiveSessions.containsKey(fromPane) || archiveSessions.containsKey(targetPane)) {
            _operationMessages.tryEmit("Symbolic links are unavailable for read-only archive views")
            return
        }
        val targetPath = if (targetPane == ActivePane.RIGHT) _rightPaneState.value.currentPath else _leftPaneState.value.currentPath
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
        viewModelScope.launch(Dispatchers.IO) {
            if (archiveSessions.values.any { isInside(request.outputDirectory, it.workspaceRoot) }) {
                _operationMessages.tryEmit("Archive views are read-only. Choose a filesystem output folder or the other panel.")
                return@launch
            }
            runCatching { ArchiveEngine.create(request) }
                .onSuccess { outputs ->
                    scheduleArchiveCommit(ActivePane.LEFT)
                    scheduleArchiveCommit(ActivePane.RIGHT)
                    refreshDirectory(ActivePane.LEFT)
                    refreshDirectory(ActivePane.RIGHT)
                    clearSelection(pane)
                    val names = outputs.joinToString { it.name }
                    _operationMessages.tryEmit("Created $names")
                }
                .onFailure { e ->
                    _operationMessages.tryEmit("Compression failed: ${e.message ?: e.javaClass.simpleName}")
                }
        }
    }

    fun openArchive(pane: ActivePane, archive: File, password: String = "") {
        updatePaneState(pane) { it.copy(isLoading = true) }
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                if (!ArchiveEngine.supports(archive)) throw IOException("Unsupported archive: ${archive.name}")
                archiveSessions[pane]?.let { current ->
                    if (isInside(archive, current.workspaceRoot)) {
                        throw IOException("Nested archive browsing is not supported yet; extract it first")
                    }
                }
                archiveSessions[pane]?.let { previous ->
                    previous.workspaceRoot.deleteRecursively()
                    archiveSessions.remove(pane)
                }

                val tempBase = File(getApplication<Application>().cacheDir, "mtapktool-archive-workspaces")
                if (!tempBase.exists() && !tempBase.mkdirs()) throw IOException("Cannot create archive workspace")
                val workspace = File(tempBase, "${pane.name.lowercase()}-${archive.name.hashCode()}-${System.nanoTime()}")
                if (!workspace.mkdirs()) throw IOException("Cannot create archive workspace: ${workspace.absolutePath}")
                try {
                    ArchiveEngine.extractToDirectory(archive, workspace, password)
                } catch (t: Throwable) {
                    workspace.deleteRecursively()
                    throw t
                }

                val session = ArchiveSession(
                    archive = archive.canonicalFile,
                    workspaceRoot = workspace.canonicalFile,
                    returnDirectory = archive.parentFile?.absolutePath ?: Environment.getExternalStorageDirectory().absolutePath,
                    password = password,
                    snapshot = workspaceSnapshot(workspace),
                )
                archiveSessions[pane] = session
                updatePaneState(pane) {
                    it.copy(
                        archiveFilePath = session.archive.absolutePath,
                        archiveRootPath = session.workspaceRoot.absolutePath,
                        highlightedItemName = null,
                    )
                }
                loadDirectory(pane, session.workspaceRoot.absolutePath)
                _operationMessages.tryEmit("Opened ${archive.name} (read-only)")
            }.onFailure { error ->
                updatePaneState(pane) { it.copy(isLoading = false) }
                _operationMessages.tryEmit("Archive open failed: ${error.message ?: error.javaClass.simpleName}")
            }
        }
    }

    fun extractArchive(pane: ActivePane, request: ArchiveExtractRequest) {
        viewModelScope.launch(Dispatchers.IO) {
            if (archiveSessions.values.any { isInside(request.outputDirectory, it.workspaceRoot) }) {
                _operationMessages.tryEmit("Archive views are read-only. Extract to a filesystem folder or the other panel.")
                return@launch
            }
            runCatching { ArchiveEngine.extract(request) }
                .onSuccess { destination ->
                    // Extraction can target either pane. If that pane currently displays
                    // an archive workspace, the newly extracted files are archive edits too.
                    scheduleArchivesAffectedBy(request.archive, destination)
                    refreshDirectory(ActivePane.LEFT)
                    refreshDirectory(ActivePane.RIGHT)
                    _operationMessages.tryEmit("Extracted to ${destination.absolutePath}")
                }
                .onFailure { error ->
                    _operationMessages.tryEmit("Extraction failed: ${error.message ?: error.javaClass.simpleName}")
                }
        }
    }

    /** Archive browsing is read-only; there is intentionally nothing to commit. */
    fun commitMountedArchives() = Unit


    fun closeArchive(pane: ActivePane, saveChanges: Boolean = false) {
        viewModelScope.launch(Dispatchers.IO) {
            val session = archiveSessions.remove(pane) ?: return@launch
            session.workspaceRoot.deleteRecursively()
            updatePaneState(pane) {
                it.copy(
                    archiveFilePath = null,
                    archiveRootPath = null,
                    highlightedItemName = session.archive.name,
                )
            }
            loadDirectory(pane, session.returnDirectory, isHistoryAction = true)
        }
    }


    fun navigateToDisplayPath(pane: ActivePane, value: String) {
        val text = value.trim()
        val session = archiveSessions[pane]
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

    private fun commitArchiveIfChangedInternal(pane: ActivePane, explicit: ArchiveSession? = null): Boolean = false

    private fun scheduleArchiveCommit(pane: ActivePane) = Unit

    private fun scheduleArchivesAffectedBy(vararg files: File) = Unit


    private fun workspaceSnapshot(root: File): Map<String, ArchiveStamp> {
        if (!root.isDirectory) return emptyMap()
        return root.walkTopDown()
            .filter { it != root }
            .associate { file ->
                val relative = file.relativeTo(root).invariantSeparatorsPath
                relative to ArchiveStamp(file.isDirectory, if (file.isFile) file.length() else 0L, file.lastModified())
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
    fun deleteSelected(pane: ActivePane) {
        if (archiveSessions.containsKey(pane)) {
            _operationMessages.tryEmit("Archive views are read-only")
            return
        }
        val state = if (pane == ActivePane.LEFT) _leftPaneState.value else _rightPaneState.value
        val itemsToDelete = state.selectedPaths
        viewModelScope.launch(Dispatchers.IO) {
            itemsToDelete.forEach { path ->
                File(path).deleteRecursively()
            }
            scheduleArchiveCommit(pane)
            refreshDirectory(pane)
            clearSelection(pane)
        }
    }

    // --- Create New File or Folder ---
    fun createNewItem(pane: ActivePane, name: String, isFolder: Boolean): String? {
        if (archiveSessions.containsKey(pane)) return "Archive views are read-only. Copy files out before editing."
        val currentPath = if (pane == ActivePane.LEFT) _leftPaneState.value.currentPath else _rightPaneState.value.currentPath
        val targetFile = File(currentPath, name)

        if (targetFile.exists()) return "An item with this name already exists!"

        val success = if (isFolder) targetFile.mkdirs() else targetFile.createNewFile()
        if (success) {
            scheduleArchiveCommit(pane)
            refreshDirectory(pane)
            return "Created ${if (isFolder) "folder" else "file"} successfully."
        } else {
            return "Failed to create ${if (isFolder) "folder" else "file"}."
        }
    }

    fun clearSelection(pane: ActivePane) {
        updatePaneState(pane) { it.copy(selectedPaths = emptySet()) }
    }

    fun renameItem(pane: ActivePane, oldPath: String, newName: String) {
        if (archiveSessions.containsKey(pane)) {
            _operationMessages.tryEmit("Archive views are read-only")
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            val oldFile = File(oldPath)
            if (oldFile.exists()) {
                val newFile = File(oldFile.parent, newName)
                if (oldFile.renameTo(newFile)) {
                    scheduleArchiveCommit(pane)
                    refreshDirectory(pane)
                }
            }
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

    fun removeFolderSortOverride(pane: ActivePane, path: String) {
        folderSortOverrides.remove(sortKey(pane, path))
        val state = paneState(pane)
        if (state.currentPath == path) {
            updatePaneState(pane) { it.copy(sortSpec = defaultSort(pane)) }
        }
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
    fun navigateToDirectPath(pane: ActivePane, fullPath: String) {
        val target = File(fullPath.trim())
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

        // Save highlight state into PaneState
        if (pane == ActivePane.LEFT) {
            _leftPaneState.update { it.copy(highlightedItemName = highlightFileName) }
        } else {
            _rightPaneState.update { it.copy(highlightedItemName = highlightFileName) }
        }
    }
    override fun onCleared() {
        archiveSessions.values.forEach { it.workspaceRoot.deleteRecursively() }
        archiveSessions.clear()
        super.onCleared()
    }

    companion object {
        private const val RECENT_HIGHLIGHT_MS = 120_000L
    }
}
