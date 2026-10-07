package io.github.lootdev78.mtapktool.feature.ftp

import java.io.File

data class FtpFileInfo(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val size: Long,
    val modifiedTime: Long
) {
    fun toFile(): File = File(path)
}