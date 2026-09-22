package io.github.lootdev78.mtapktool.apktool

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.lifecycle.AndroidViewModel
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class ApktoolJobInfo(
    val id: String,
    val title: String,
    val command: String,
    val status: String,
    val stage: String = ApktoolWorkflowStage.QUEUED.name,
    val line: String,
    val output: String?,
    val createdAt: Long,
    val log: String = "",
) {
    val isTerminal: Boolean get() = status in setOf("SUCCEEDED", "FAILED", "CANCELLED")
}

class ApktoolJobsViewModel(application: Application) : AndroidViewModel(application) {
    private val _jobs = MutableStateFlow<List<ApktoolJobInfo>>(emptyList())
    val jobs: StateFlow<List<ApktoolJobInfo>> = _jobs.asStateFlow()

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != ApktoolJobService.ACTION_STATUS) return
            val id = intent.getStringExtra(ApktoolJobService.EXTRA_JOB_ID) ?: return
            val update = ApktoolJobInfo(
                id = id,
                title = intent.getStringExtra(ApktoolJobService.EXTRA_TITLE).orEmpty(),
                command = intent.getStringExtra(ApktoolJobService.EXTRA_COMMAND).orEmpty(),
                status = intent.getStringExtra(ApktoolJobService.EXTRA_STATUS).orEmpty(),
                stage = intent.getStringExtra(ApktoolJobService.EXTRA_STAGE).orEmpty().ifBlank { ApktoolWorkflowStage.QUEUED.name },
                line = intent.getStringExtra(ApktoolJobService.EXTRA_LINE).orEmpty(),
                output = intent.getStringExtra(ApktoolJobService.EXTRA_OUTPUT),
                createdAt = intent.getLongExtra(ApktoolJobService.EXTRA_CREATED_AT, System.currentTimeMillis()),
                log = intent.getStringExtra(ApktoolJobService.EXTRA_LOG).orEmpty(),
            )
            _jobs.update { old ->
                (old.filterNot { it.id == id } + update).sortedByDescending { it.createdAt }
            }
        }
    }

    init {
        val app = getApplication<Application>()
        val filter = IntentFilter(ApktoolJobService.ACTION_STATUS)
        ContextCompat.registerReceiver(app, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        ApktoolJobService.query(app)
    }

    fun cancel(id: String) = ApktoolJobService.cancel(getApplication(), id)
    fun cancelAll() = ApktoolJobService.cancelAll(getApplication())

    fun dismiss(id: String) {
        val job = _jobs.value.firstOrNull { it.id == id } ?: return
        if (!job.isTerminal) return
        _jobs.update { jobs -> jobs.filterNot { it.id == id } }
        ApktoolJobService.dismiss(getApplication(), id)
    }

    fun clearFinished() {
        _jobs.update { jobs -> jobs.filterNot { it.isTerminal } }
        ApktoolJobService.clearFinished(getApplication())
    }

    override fun onCleared() {
        runCatching { getApplication<Application>().unregisterReceiver(receiver) }
        super.onCleared()
    }
}
