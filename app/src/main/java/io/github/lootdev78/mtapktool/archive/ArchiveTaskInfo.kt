package io.github.lootdev78.mtapktool.archive

import io.github.lootdev78.mtapktool.feature.explorer.viewmodel.ActivePane

enum class ExplorerTaskKind { ARCHIVE_CREATE, ARCHIVE_EXTRACT, ARCHIVE_UPDATE, COPY, MOVE, DELETE }

data class ArchiveTaskInfo(
    val id: String,
    val title: String,
    val detail: String,
    val progress: Int? = null,
    val kind: ExplorerTaskKind = ExplorerTaskKind.ARCHIVE_CREATE,
)

data class ArchiveUpdateRequest(
    val pane: ActivePane,
    val archiveName: String,
    val changedEntries: Int,
    val isApk: Boolean,
    val readOnly: Boolean,
    val closeAfterUpdate: Boolean = false,
)
