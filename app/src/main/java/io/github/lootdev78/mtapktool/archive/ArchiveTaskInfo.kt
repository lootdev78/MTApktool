package io.github.lootdev78.mtapktool.archive

enum class ArchiveTaskKind { OPEN, CREATE, EXTRACT, UPDATE }
enum class ArchiveTaskStatus { QUEUED, RUNNING, SUCCEEDED, FAILED, CANCELLED }

data class ArchiveTaskInfo(
    val id: Long,
    val kind: ArchiveTaskKind,
    val title: String,
    val detail: String,
    val status: ArchiveTaskStatus = ArchiveTaskStatus.QUEUED,
    val progress: Int? = null,
    val message: String = "",
    val createdAt: Long = System.currentTimeMillis(),
) {
    val isTerminal: Boolean get() = status in setOf(ArchiveTaskStatus.SUCCEEDED, ArchiveTaskStatus.FAILED, ArchiveTaskStatus.CANCELLED)
}
