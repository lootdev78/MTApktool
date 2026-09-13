package io.github.lootdev78.mtapktool.feature.explorer.state

import androidx.compose.runtime.Immutable
import io.github.lootdev78.mtapktool.feature.explorer.model.FileItem

@Immutable
data class PaneState(
    val currentPath: String = "/storage/emulated/0",
    val items: List<FileItem> = emptyList(),
    val selectedPaths: Set<String> = emptySet(),
    val isLoading: Boolean = false,
    val highlightedItemName: String? = null,
    val searchQuery: String = ""
) {
    val filteredItems: List<FileItem>
        get() = if (searchQuery.isEmpty()) {
            items
        } else {
            items.filter {
                it.name.contains(searchQuery, ignoreCase = true)
            }
        }

    val folderCount: Int
        get() = filteredItems.count { it.isDirectory }

    val fileCount: Int
        get() = filteredItems.count { !it.isDirectory }
}