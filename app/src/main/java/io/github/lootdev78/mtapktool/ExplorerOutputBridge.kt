package io.github.lootdev78.mtapktool

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** One-shot hand-off used by tool screens that need to return an output to the explorer. */
object ExplorerOutputBridge {
    private val _outputPath = MutableStateFlow<String?>(null)
    val outputPath: StateFlow<String?> = _outputPath.asStateFlow()

    fun publish(path: String) {
        _outputPath.value = path
    }

    fun clear() {
        _outputPath.value = null
    }
}
