package io.github.lootdev78.mtapktool.feature.editor

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class FileDetailsState(
    val isLoading: Boolean = true,
    val formattedSize: String = "Calculating...",
    val totalSizeBytes: Long = 0,
    val fileCount: Int = 0,
    val dirCount: Int = 0,
    val lines: Int? = null,
    val words: Int? = null,
    val chars: Int? = null,
    val sha256: String? = null,
    val permissions: String = ""
)

@Composable
fun FileInfoDialog(
    file: File,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var detailsState by remember(file) { mutableStateOf(FileDetailsState()) }

    // Calculate background statistics on IO thread
    LaunchedEffect(file) {
        withContext(Dispatchers.IO) {
            val permissions = buildString {
                append(if (file.canRead()) "r" else "-")
                append(if (file.canWrite()) "w" else "-")
                append(if (file.canExecute()) "x" else "-")
            }

            if (file.isDirectory) {
                var totalBytes = 0L
                var filesNum = 0
                var dirsNum = 0

                file.walkTopDown().forEach { item ->
                    if (item != file) {
                        if (item.isDirectory) dirsNum++ else if (item.isFile) {
                            filesNum++
                            totalBytes += item.length()
                        }
                    }
                }

                detailsState = FileDetailsState(
                    isLoading = false,
                    formattedSize = formatFileSize(totalBytes),
                    totalSizeBytes = totalBytes,
                    fileCount = filesNum,
                    dirCount = dirsNum,
                    permissions = permissions
                )
            } else {
                val sizeBytes = file.length()
                var lineCount: Int? = null
                var wordCount: Int? = null
                var charCount: Int? = null

                if (sizeBytes < 10 * 1024 * 1024) { // Parse text stats if file < 10 MB
                    runCatching {
                        val text = file.readText()
                        lineCount = text.lines().size
                        charCount = text.length
                        wordCount = text.split(Regex("\\s+")).count { it.isNotEmpty() }
                    }
                }

                val hash = runCatching {
                    val digest = MessageDigest.getInstance("SHA-256")
                    file.inputStream().use { fis ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        while (fis.read(buffer).also { bytesRead = it } != -1) {
                            digest.update(buffer, 0, bytesRead)
                        }
                    }
                    digest.digest().joinToString("") { "%02x".format(it) }
                }.getOrDefault("Error computing hash")

                detailsState = FileDetailsState(
                    isLoading = false,
                    formattedSize = formatFileSize(sizeBytes),
                    totalSizeBytes = sizeBytes,
                    lines = lineCount,
                    words = wordCount,
                    chars = charCount,
                    sha256 = hash,
                    permissions = permissions
                )
            }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(2.dp),
            color = MaterialTheme.colorScheme.surface,
            contentColor =  MaterialTheme.colorScheme.onSurface,
            tonalElevation = 8.dp,
            modifier = Modifier.fillMaxWidth(0.98f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp)
            ) {
                Text(
                    text = "Properties",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 12.dp,).padding(bottom = 12.dp)
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                Column(
                    modifier = Modifier
                        .weight(weight = 1f, fill = false)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp)
                ) {
                    TwoColumnInfoRow(context, label = "Name", value = file.name)
                    TwoColumnInfoRow(context, label = "Parent Path", value = file.parent ?: "/")
                    TwoColumnInfoRow(context, label = "Full Path", value = file.absolutePath)
                    TwoColumnInfoRow(
                        context,
                        label = "Type",
                        value = if (file.isDirectory) "Directory / Folder" else "${file.extension.uppercase()} File"
                    )

                    val formattedDate = remember(file.lastModified()) {
                        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                            .format(Date(file.lastModified()))
                    }
                    TwoColumnInfoRow(context, label = "Modified", value = formattedDate)
                    TwoColumnInfoRow(context, label = "Permissions", value = detailsState.permissions)

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 8.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                    )

                    if (detailsState.isLoading) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 12.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "Calculating size & details...",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    } else {
                        TwoColumnInfoRow(
                            context,
                            label = "Total Size",
                            value = "${detailsState.formattedSize} (${detailsState.totalSizeBytes} bytes)"
                        )

                        if (file.isDirectory) {
                            TwoColumnInfoRow(
                                context,
                                label = "Contents",
                                value = "${detailsState.fileCount} Files, ${detailsState.dirCount} Folders"
                            )
                        } else {
                            if (detailsState.lines != null) {
                                TwoColumnInfoRow(
                                    context,
                                    label = "Statistics",
                                    value = "${detailsState.lines} lines, ${detailsState.words} words, ${detailsState.chars} chars"
                                )
                            }
                            detailsState.sha256?.let { hash ->
                                TwoColumnInfoRow(
                                    context,
                                    label = "SHA-256",
                                    value = hash,
                                    isMonospace = true
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    Button(onClick = onDismiss, shape = RectangleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.Transparent,
                            contentColor = MaterialTheme.colorScheme.primary,

                        ),
                        contentPadding = PaddingValues(0.dp),
                        modifier = Modifier
                            .background(Color.Transparent)
                            .padding(4.dp)
                        ) {
                        Text("Close")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TwoColumnInfoRow(
    context: Context,
    label: String,
    value: String,
    isMonospace: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = {
                    copyToClipboard(context, label, value)
                }
            )
            .padding(vertical = 2.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.32f)
        )

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = value,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurface,
            fontFamily = if (isMonospace) FontFamily.Monospace else FontFamily.Default,
            modifier = Modifier.weight(0.68f)
        )
    }
}

private fun copyToClipboard(context: Context, label: String, value: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText(label, value)
    clipboard.setPrimaryClip(clip)
    Toast.makeText(context, "Copied $label to clipboard", Toast.LENGTH_SHORT).show()
}

private fun formatFileSize(sizeInBytes: Long): String {
    if (sizeInBytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(sizeInBytes.toDouble()) / Math.log10(1024.0)).toInt()
    val value = sizeInBytes / Math.pow(1024.0, digitGroups.toDouble())
    return "%.2f %s".format(Locale.US, value, units[digitGroups])
}