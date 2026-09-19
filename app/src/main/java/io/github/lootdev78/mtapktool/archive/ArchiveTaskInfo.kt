package io.github.lootdev78.mtapktool.archive

data class ArchiveTaskInfo(
    val id: String,
    val title: String,
    val detail: String,
    val progress: Int? = null,
)
