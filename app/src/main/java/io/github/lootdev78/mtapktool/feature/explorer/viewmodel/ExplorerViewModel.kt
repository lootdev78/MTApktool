package io.github.lootdev78.mtapktool.feature.explorer.viewmodel

import android.os.Environment
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.lootdev78.mtapktool.feature.explorer.model.FileItem
import io.github.lootdev78.mtapktool.feature.explorer.state.PaneState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Stack

enum class ActivePane { LEFT, RIGHT }

class ExplorerViewModel(private val savedStateHandle: SavedStateHandle) : ViewModel() {

    private val _leftPaneState = MutableStateFlow(PaneState())
    val leftPaneState: StateFlow<PaneState> = _leftPaneState.asStateFlow()

    private val _rightPaneState = MutableStateFlow(PaneState())
    val rightPaneState: StateFlow<PaneState> = _rightPaneState.asStateFlow()


    private val _activePane = MutableStateFlow(ActivePane.LEFT)
    val activePane: StateFlow<ActivePane> = _activePane.asStateFlow()

    // Separate Back/Forward stacks for dual pane navigation history
    private val leftBackStack = Stack<String>()
    private val leftForwardStack = Stack<String>()

    private val rightBackStack = Stack<String>()
    private val rightForwardStack = Stack<String>()


    init {
        val rootPath = Environment.getExternalStorageDirectory().absolutePath
        val leftPath = savedDirectory(KEY_LEFT_PATH, rootPath)
        val rightPath = savedDirectory(KEY_RIGHT_PATH, rootPath)
        _activePane.value = runCatching {
            ActivePane.valueOf(savedStateHandle.get<String>(KEY_ACTIVE_PANE).orEmpty())
        }.getOrDefault(ActivePane.LEFT)

        loadDirectory(ActivePane.LEFT, leftPath, isHistoryAction = true)
        loadDirectory(ActivePane.RIGHT, rightPath, isHistoryAction = true)
    }

    fun setActive(pane: ActivePane) {
        _activePane.value = pane
        savedStateHandle[KEY_ACTIVE_PANE] = pane.name
    }

    fun loadDirectory(pane: ActivePane, path: String, isHistoryAction: Boolean = false) {
        val currentPath = if (pane == ActivePane.LEFT) _leftPaneState.value.currentPath else _rightPaneState.value.currentPath
        val targetDir = File(path).absoluteFile
        val resolvedPath = if (targetDir.isDirectory) targetDir.absolutePath else Environment.getExternalStorageDirectory().absolutePath

        // If navigating to a new path (not back/forward action), save to back stack & clear forward stack
        if (!isHistoryAction && currentPath.isNotEmpty() && currentPath != resolvedPath) {
            if (pane == ActivePane.LEFT) {
                leftBackStack.push(currentPath)
                leftForwardStack.clear()
            } else {
                rightBackStack.push(currentPath)
                rightForwardStack.clear()
            }
        }

        viewModelScope.launch {
            updatePaneState(pane) { it.copy(isLoading = true, currentPath = resolvedPath) }
            savedStateHandle[if (pane == ActivePane.LEFT) KEY_LEFT_PATH else KEY_RIGHT_PATH] = resolvedPath

            val files = withContext(Dispatchers.IO) {
                val dir = File(resolvedPath)
                if (dir.exists() && dir.isDirectory) {
                    dir.listFiles()
                        ?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
                        ?.map { FileItem(file = it) } ?: emptyList()
                } else {
                    emptyList()
                }
            }

            updatePaneState(pane) {
                it.copy(items = files, isLoading = false, selectedPaths = emptySet())
            }
        }
    }

    // --- Navigation History Controls ---
    fun navigateHistoryBack(pane: ActivePane) {
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

                val startIndex = state.items.indexOfFirst {
                    it.path == firstSelected
                }

                val endIndex = state.items.indexOfFirst {
                    it.path == itemPath
                }

                if (startIndex != -1 && endIndex != -1) {
                    val start = minOf(startIndex, endIndex)
                    val end = maxOf(startIndex, endIndex)

                    for (i in start..end) {
                        selected.add(state.items[i].path)
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
        val current = if (pane == ActivePane.LEFT) _leftPaneState.value.currentPath else _rightPaneState.value.currentPath
        val rootPath = Environment.getExternalStorageDirectory().absolutePath

        if (current == rootPath || current == "/") return

        val parent = File(current).parent
        if (parent != null) {
            loadDirectory(pane, parent)
        }
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
            val allPaths = state.items.map { it.path }.toSet()
            state.copy(selectedPaths = allPaths)
        }
    }



    fun invertSelection(pane: ActivePane) {
        updatePaneState(pane) { state ->
            val currentSelected = state.selectedPaths
            val inverted = state.items
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
        val sourcePath = if (activePane.value == ActivePane.LEFT) {
            _leftPaneState.value.currentPath
        } else {
            _rightPaneState.value.currentPath
        }

        val targetPane = if (activePane.value == ActivePane.LEFT) ActivePane.RIGHT else ActivePane.LEFT

        // Load the active pane's current directory into the opposite pane
        loadDirectory(pane = targetPane, path = sourcePath)
    }

    fun copySelectedToOppositePane(fromPane: ActivePane) {
        val sourceState = if (fromPane == ActivePane.LEFT) _leftPaneState.value else _rightPaneState.value
        val targetPane = if (fromPane == ActivePane.LEFT) ActivePane.RIGHT else ActivePane.LEFT
        val targetPath = if (targetPane == ActivePane.RIGHT) _rightPaneState.value.currentPath else _leftPaneState.value.currentPath

        val itemsToCopy = sourceState.selectedPaths
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                itemsToCopy.forEach { sourcePath ->
                    val sourceFile = File(sourcePath)
                    if (sourceFile.exists()) {
                        val destFile = File(targetPath, sourceFile.name)
                        if (sourceFile.isDirectory) {
                            sourceFile.copyRecursively(destFile, overwrite = true)
                        } else {
                            sourceFile.copyTo(destFile, overwrite = true)
                        }
                    }
                }
            }.onSuccess {
                refreshDirectory(targetPane)
                clearSelection(fromPane)
            }.onFailure { e ->
                // Log or handle error
                e.printStackTrace()
            }
        }
    }

    fun moveSelectedToOppositePane(fromPane: ActivePane) {
        val sourceState = if (fromPane == ActivePane.LEFT) _leftPaneState.value else _rightPaneState.value
        val targetPane = if (fromPane == ActivePane.LEFT) ActivePane.RIGHT else ActivePane.LEFT
        val targetPath = if (targetPane == ActivePane.RIGHT) _rightPaneState.value.currentPath else _leftPaneState.value.currentPath

        val itemsToMove = sourceState.selectedPaths
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                itemsToMove.forEach { sourcePath ->
                    val sourceFile = File(sourcePath)
                    if (sourceFile.exists()) {
                        val destFile = File(targetPath, sourceFile.name)
                        sourceFile.renameTo(destFile)
                    }
                }
            }.onSuccess {
                refreshDirectory(fromPane)
                refreshDirectory(targetPane)
                clearSelection(fromPane)
            }.onFailure { e ->
                e.printStackTrace()
            }
        }
    }

    // --- Batch Delete ---
    fun deleteSelected(pane: ActivePane) {
        val state = if (pane == ActivePane.LEFT) _leftPaneState.value else _rightPaneState.value
        val itemsToDelete = state.selectedPaths
        viewModelScope.launch(Dispatchers.IO) {
            itemsToDelete.forEach { path ->
                File(path).deleteRecursively()
            }
            refreshDirectory(pane)
            clearSelection(pane)
        }
    }

    // --- Create New File or Folder ---
    fun createNewItem(pane: ActivePane, name: String, isFolder: Boolean): String? {
        val currentPath = if (pane == ActivePane.LEFT) _leftPaneState.value.currentPath else _rightPaneState.value.currentPath
        val targetFile = File(currentPath, name)

        if (targetFile.exists()) return "An item with this name already exists!"

        val success = if (isFolder) targetFile.mkdirs() else targetFile.createNewFile()
        if (success) {
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
        viewModelScope.launch(Dispatchers.IO) {
            val oldFile = File(oldPath)
            if (oldFile.exists()) {
                val newFile = File(oldFile.parent, newName)
                if (oldFile.renameTo(newFile)) {
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

    // --- Direct Path Navigation with Scroll/Highlight Target ---
    fun navigateToDirectPath(pane: ActivePane, fullPath: String) {
        val target = File(fullPath)
        if (!target.exists()) return

        val directoryPath = if (target.isDirectory) target.absolutePath else target.parent ?: return
        val highlightFileName = if (target.isDirectory) null else target.name

        loadDirectory(pane, directoryPath)

        // Save highlight state into PaneState
        if (pane == ActivePane.LEFT) {
            _leftPaneState.update { it.copy(highlightedItemName = highlightFileName) }
        } else {
            _rightPaneState.update { it.copy(highlightedItemName = highlightFileName) }
        }
    }

    private fun savedDirectory(key: String, fallback: String): String {
        val saved = savedStateHandle.get<String>(key).orEmpty()
        return saved.takeIf { it.isNotBlank() && File(it).isDirectory } ?: fallback
    }

    private companion object {
        const val KEY_LEFT_PATH = "explorer_left_path"
        const val KEY_RIGHT_PATH = "explorer_right_path"
        const val KEY_ACTIVE_PANE = "explorer_active_pane"
    }

}