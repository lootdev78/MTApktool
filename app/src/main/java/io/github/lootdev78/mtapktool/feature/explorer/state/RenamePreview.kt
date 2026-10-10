package io.github.lootdev78.mtapktool.feature.explorer.state

import io.github.lootdev78.mtapktool.feature.explorer.util.FileWorkflow
import io.github.lootdev78.mtapktool.feature.explorer.viewmodel.ActivePane

data class RenamePreview(
    val pane: ActivePane,
    val directory: String,
    val entries: List<FileWorkflow.RenameEntry>,
    val parents: Map<String, String>,
)
