package io.github.lootdev78.mtapktool.tasks

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

enum class ToolTaskStatus { RUNNING, SUCCEEDED, FAILED, CANCELLED }
data class ToolTaskInfo(
    val id: String,
    val title: String,
    val detail: String,
    val status: ToolTaskStatus = ToolTaskStatus.RUNNING,
    val progress: Int? = null,
    val message: String = detail,
    val log: String = detail,
    val outputPath: String? = null,
    val canCancel: Boolean = false,
) { val isTerminal: Boolean get() = status != ToolTaskStatus.RUNNING }

/** All native tools share the explorer's task drawer; APK jobs keep their existing worker service. */
object ToolTaskRegistry {
    private val mutableTasks = MutableStateFlow<List<ToolTaskInfo>>(emptyList())
    val tasks = mutableTasks.asStateFlow()
    private val cancellations = ConcurrentHashMap<String, () -> Unit>()

    fun begin(title: String, detail: String = "", onCancel: (() -> Unit)? = null): String {
        val id = UUID.randomUUID().toString()
        if (onCancel != null) cancellations[id] = onCancel
        mutableTasks.update { tasks -> listOf(ToolTaskInfo(id, title, detail, canCancel = onCancel != null)) + tasks.filter { !it.isTerminal }.take(100) + tasks.filter { it.isTerminal }.take(50) }
        return id
    }
    fun progress(id: String, progress: Int? = null, message: String? = null) {
        mutableTasks.update { tasks -> tasks.map { task ->
            if (task.id != id || task.isTerminal) task else task.copy(progress = progress?.coerceIn(0, 100) ?: task.progress, message = message ?: task.message,
                log = if (message != null && message != task.message) (task.log + "\n" + message).takeLast(12000) else task.log)
        } }
    }
    fun finish(id: String, status: ToolTaskStatus = ToolTaskStatus.SUCCEEDED, message: String = "Fertig", outputPath: String? = null) {
        cancellations.remove(id)
        mutableTasks.update { tasks -> tasks.map { task ->
            if (task.id != id || task.isTerminal) task else task.copy(status = status, progress = if (status == ToolTaskStatus.SUCCEEDED) 100 else task.progress, message = message, log = task.log + "\n" + message, outputPath = outputPath)
        } }
    }
    fun cancel(id: String) {
        val callback = cancellations.remove(id) ?: return
        finish(id, ToolTaskStatus.CANCELLED, "Abgebrochen")
        runCatching(callback)
    }
    fun cancelAll() = cancellations.keys.toList().forEach(::cancel)
    fun clearFinished() { mutableTasks.update { tasks -> tasks.filterNot { it.isTerminal } } }

    suspend fun <T> run(title: String, detail: String = "", onCancel: () -> Unit = {}, block: suspend (String) -> T): T {
        val job = currentCoroutineContext()[Job]
        val id = begin(title, detail) { job?.cancel(); onCancel() }
        try {
            val result = block(id)
            finish(id)
            return result
        } catch (cancelled: CancellationException) {
            finish(id, ToolTaskStatus.CANCELLED, "Abgebrochen")
            throw cancelled
        } catch (error: Exception) {
            finish(id, ToolTaskStatus.FAILED, error.message ?: error.javaClass.simpleName)
            throw error
        }
    }
}
