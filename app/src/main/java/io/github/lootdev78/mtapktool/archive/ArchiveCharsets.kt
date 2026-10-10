package io.github.lootdev78.mtapktool.archive

import java.nio.charset.Charset

data class ArchiveCharsetChoice(val label: String, val value: String)

object ArchiveCharsets {
    val choices: List<ArchiveCharsetChoice> = listOf(
        ArchiveCharsetChoice("Automatisch (Standard)", ""),
        ArchiveCharsetChoice("UTF-8", "UTF-8"),
        ArchiveCharsetChoice("GBK", "GBK"),
        ArchiveCharsetChoice("Big5", "Big5"),
        ArchiveCharsetChoice("Shift JIS", "Shift_JIS"),
        ArchiveCharsetChoice("Windows-1251", "windows-1251"),
        ArchiveCharsetChoice("Windows-1252", "windows-1252"),
        ArchiveCharsetChoice("CP437 / IBM437", "IBM437"),
        ArchiveCharsetChoice("ISO-8859-1", "ISO-8859-1"),
        ArchiveCharsetChoice("EUC-KR", "EUC-KR"),
    ).filter { it.value.isEmpty() || Charset.isSupported(it.value) }

    fun supports(format: ArchiveFormat?): Boolean = format in setOf(
        ArchiveFormat.ZIP, ArchiveFormat.TAR, ArchiveFormat.TAR_GZ, ArchiveFormat.TAR_XZ,
        ArchiveFormat.TAR_ZST, ArchiveFormat.TAR_BZ2, ArchiveFormat.TAR_LZ4,
    )

    fun charset(value: String): Charset? = value.takeIf { it.isNotBlank() }?.let(Charset::forName)
}
