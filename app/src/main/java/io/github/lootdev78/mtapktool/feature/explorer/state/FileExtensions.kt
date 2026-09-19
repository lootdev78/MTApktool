package io.github.lootdev78.mtapktool.feature.explorer.state

import io.github.lootdev78.mtapktool.feature.explorer.model.FileItem
import java.io.File

private val EDITABLE_EXTENSIONS = setOf(
    "txt", "xml", "json",
    "py", "kt", "kts", "java",
    "c", "cpp", "cc", "cxx",
    "h", "hpp",
    "js", "jsx", "ts", "tsx",
    "html", "htm",
    "css", "scss", "sass",
    "sh", "bash", "zsh", "fish",
    "bat", "cmd",
    "md", "markdown",
    "properties",
    "gradle",
    "yml", "yaml",
    "log",
    "ini", "conf", "config",
    "smali",
    "csv",
    "sql",
    "toml",
)

private val IMAGE_EXTENSIONS = setOf(
    "jpg", "jpeg",
    "png",
    "gif",
    "webp",
    "bmp",
    "heic",
    "heif",
    "avif",
)

private val VIDEO_EXTENSIONS = setOf(
    "mp4",
    "mkv",
    "webm",
    "avi",
    "mov",
    "m4v",
    "3gp",
    "mpeg",
    "mpg",
    "ts",
)

private val AUDIO_EXTENSIONS = setOf(
    "mp3",
    "wav",
    "flac",
    "aac",
    "ogg",
    "oga",
    "m4a",
    "opus",
    "wma",
)

private val ARCHIVE_SUFFIXES = setOf(
    ".zip",
    ".jar",
    ".apk",
    ".apks",
    ".apkm",
    ".xapk",
    ".apkx",
    ".7z",
    ".tar",
    ".tar.gz",
    ".tar.xz",
    ".tar.zst",
    ".tar.bz2",
    ".tar.lz4",
    ".gz",
    ".xz",
)

private val APK_EXTENSIONS = setOf(
    "apk",
    "apks",
    "apkm",
    "xapk",
    "apkx",
)

private val PDF_EXTENSIONS = setOf(
    "pdf"
)

private val FONT_EXTENSIONS = setOf(
    "ttf",
    "otf",
    "woff",
    "woff2"
)

private val DOCUMENT_EXTENSIONS = setOf(
    "doc",
    "docx",
    "xls",
    "xlsx",
    "ppt",
    "pptx",
    "odt",
    "ods",
    "odp"
)

private val WEB_EXTENSIONS = setOf(
    "html",
    "htm",
    "css",
    "js",
    "jsx",
    "ts",
    "tsx"
)

private fun FileItem.extension(): String = extensionName.lowercase()

fun FileItem.isEditableTextFile(): Boolean {
    if (isDirectory) return false
    return extension() in EDITABLE_EXTENSIONS
}

fun FileItem.isImageFile(): Boolean {
    if (isDirectory) return false
    return extension() in IMAGE_EXTENSIONS
}

fun FileItem.isVideoFile(): Boolean {
    if (isDirectory) return false
    return extension() in VIDEO_EXTENSIONS
}

fun FileItem.isAudioFile(): Boolean {
    if (isDirectory) return false
    return extension() in AUDIO_EXTENSIONS
}

fun FileItem.isArchiveFile(): Boolean {
    if (isDirectory) return false
    val lower = name.lowercase()
    return ARCHIVE_SUFFIXES.any(lower::endsWith)
}

fun FileItem.isApkFile(): Boolean {
    if (isDirectory) return false
    return extension() in APK_EXTENSIONS
}

fun FileItem.isPdfFile(): Boolean {
    if (isDirectory) return false
    return extension() in PDF_EXTENSIONS
}

fun FileItem.isFontFile(): Boolean {
    if (isDirectory) return false
    return extension() in FONT_EXTENSIONS
}

fun FileItem.isDocumentFile(): Boolean {
    if (isDirectory) return false
    return extension() in DOCUMENT_EXTENSIONS
}

fun FileItem.isWebFile(): Boolean {
    if (isDirectory) return false
    return extension() in WEB_EXTENSIONS
}