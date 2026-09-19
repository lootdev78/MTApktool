package io.github.lootdev78.mtapktool

import android.content.Intent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ExternalOpenRequest(
    val uri: String,
    val mimeType: String?,
)

/** Bridges ACTION_VIEW/ACTION_EDIT into the already-running dual-pane explorer. */
object ExternalOpenBridge {
    private val _request = MutableStateFlow<ExternalOpenRequest?>(null)
    val request: StateFlow<ExternalOpenRequest?> = _request.asStateFlow()

    fun publish(intent: Intent?) {
        val action = intent?.action ?: return
        if (action != Intent.ACTION_VIEW && action != Intent.ACTION_EDIT) return
        val data = intent.data ?: return
        _request.value = ExternalOpenRequest(data.toString(), intent.type)
    }

    fun clear() {
        _request.value = null
    }
}
