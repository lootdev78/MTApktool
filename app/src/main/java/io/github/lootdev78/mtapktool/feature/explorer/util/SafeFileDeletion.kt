package io.github.lootdev78.mtapktool.feature.explorer.util

import java.io.File

/** Match File.deleteRecursively's result while removing links without traversing their targets. */
fun File.deleteTreeSafely(): Boolean = runCatching { FileWorkflow.deleteTree(toPath()); true }.getOrDefault(false)
