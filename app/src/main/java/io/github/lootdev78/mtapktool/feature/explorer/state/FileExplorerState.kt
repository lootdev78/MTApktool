package io.github.lootdev78.mtapktool.feature.explorer.state

import androidx.compose.runtime.Immutable
import io.github.lootdev78.mtapktool.feature.explorer.model.FileItem
import java.io.File

enum class SortField { NAME, SIZE, DATE, TYPE }

data class SortSpec(
    val field: SortField = SortField.NAME,
    val descending: Boolean = false,
)

enum class FileFilter {
    ALL,
    FOLDERS,
    FILES,
    APK,
    ARCHIVE,
    IMAGE,
    AUDIO,
    VIDEO,
    DOCUMENT,
    RECENT,
}

@Immutable
data class PaneState(
    val currentPath: String = "/storage/emulated/0",
    val items: List<FileItem> = emptyList(),
    val selectedPaths: Set<String> = emptySet(),
    val isLoading: Boolean = false,
    val loadingProgress: Int? = null,
    val loadingLabel: String? = null,
    val highlightedItemName: String? = null,
    val highlightedItemPath: String? = null,
    val highlightEvent: Long = 0L,
    val searchQuery: String = "",
    val searchResultsLabel: String? = null,
    val showSystemHidden: Boolean = true,
    val showManuallyHidden: Boolean = true,
    val manuallyHiddenPaths: Set<String> = emptySet(),
    val sortSpec: SortSpec = SortSpec(),
    val filter: FileFilter = FileFilter.ALL,
    val recentlyChangedPaths: Set<String> = emptySet(),
    val archiveFilePath: String? = null,
    val archiveDisplayPath: String? = null,
    val archiveRootPath: String? = null,
    val displayPathOverride: String? = null,
    val archiveStatus: io.github.lootdev78.mtapktool.archive.ArchiveSessionState? = null,
    val archiveChanges: Int = 0,
    val archiveCharset: String = "",
) {

    val isFtpView: Boolean get() = currentPath.startsWith("mtftp://")

    val isArchiveView: Boolean
        get() = archiveFilePath != null && archiveRootPath != null

    val displayPath: String
        get() {
            val archivePath = archiveDisplayPath ?: archiveFilePath
            val rootPath = archiveRootPath
            val base = displayPathOverride ?: if (archivePath != null && rootPath != null) {
                val relative = runCatching { File(currentPath).relativeTo(File(rootPath)).invariantSeparatorsPath }.getOrDefault("")
                if (relative.isBlank() || relative == ".") "$archivePath!/" else "$archivePath!/$relative"
            } else currentPath
            return searchResultsLabel?.let { "$base ($it)" } ?: base
        }

    val filteredItems: List<FileItem>
        get() {
            val now = System.currentTimeMillis()
            val visible = items.asSequence()
                .filter { showSystemHidden || !it.name.startsWith(".") || it.name == highlightedItemName }
                .filter { showManuallyHidden || it.path !in manuallyHiddenPaths || it.name == highlightedItemName }
                .filter { searchQuery.isBlank() || it.name.contains(searchQuery, ignoreCase = true) }
                .filter { item ->
                    when (filter) {
                        FileFilter.ALL -> true
                        FileFilter.FOLDERS -> item.isDirectory
                        FileFilter.FILES -> !item.isDirectory
                        FileFilter.APK -> item.isApkFile()
                        FileFilter.ARCHIVE -> item.isArchiveFile()
                        FileFilter.IMAGE -> item.isImageFile()
                        FileFilter.AUDIO -> item.isAudioFile()
                        FileFilter.VIDEO -> item.isVideoFile()
                        FileFilter.DOCUMENT -> item.isPdfFile() || item.isDocumentFile() || item.isEditableTextFile()
                        FileFilter.RECENT -> now - item.modifiedAt <= RECENT_WINDOW_MS
                    }
                }
                .toList()

            val base = compareBy<FileItem> { !it.isDirectory }
            val fieldComparator = when (sortSpec.field) {
                SortField.NAME -> compareBy<FileItem> { it.name.lowercase() }
                SortField.SIZE -> compareBy<FileItem> { it.fileSize }
                SortField.DATE -> compareBy<FileItem> { it.modifiedAt }
                SortField.TYPE -> compareBy<FileItem> { it.extensionName }.thenBy { it.name.lowercase() }
            }
            val comparator = if (sortSpec.descending) fieldComparator.reversed() else fieldComparator
            return visible.sortedWith(base.then(comparator))
        }

    val folderCount: Int
        get() = filteredItems.count { it.isDirectory }

    val fileCount: Int
        get() = filteredItems.count { !it.isDirectory }

    companion object {
        private const val RECENT_WINDOW_MS = 60L * 60L * 1000L
    }
}
