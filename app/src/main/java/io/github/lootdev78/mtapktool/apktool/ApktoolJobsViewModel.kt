package io.github.lootdev78.mtapktool.apktool

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class ApktoolJobInfo(
    val id: String,
    val title: String,
    val command: String,
    val status: String,
    val line: String,
    val output: String?,
    val createdAt: Long,
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
                line = intent.getStringExtra(ApktoolJobService.EXTRA_LINE).orEmpty(),
                output = intent.getStringExtra(ApktoolJobService.EXTRA_OUTPUT),
                createdAt = intent.getLongExtra(ApktoolJobService.EXTRA_CREATED_AT, System.currentTimeMillis()),
            )
            _jobs.update { old -> (old.filterNot { it.id == id } + update).sortedByDescending { it.createdAt } }
        }
    }

    init {
        val app = getApplication<Application>()
        val filter = IntentFilter(ApktoolJobService.ACTION_STATUS)
        if (Build.VERSION.SDK_INT >= 33) app.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        else @Suppress("DEPRECATION") app.registerReceiver(receiver, filter)
        ApktoolJobService.query(app)
    }

    fun cancel(id: String) = ApktoolJobService.cancel(getApplication(), id)
    fun cancelAll() = ApktoolJobService.cancelAll(getApplication())

    override fun onCleared() {
        runCatching { getApplication<Application>().unregisterReceiver(receiver) }
        super.onCleared()
    }
}
