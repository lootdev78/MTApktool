package io.github.lootdev78.mtapktool.archive

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import java.io.File

@Composable
fun ArchiveCreateDialog(
    sources: List<File>,
    currentDirectory: File,
    oppositeDirectory: File,
    onDismiss: () -> Unit,
    onCreate: (ArchiveRequest) -> Unit,
) {
    val context = LocalContext.current
    val defaults = remember { ArchiveSettings.load(context) }
    var format by remember { mutableStateOf(defaults.format) }
    var level by remember { mutableStateOf(defaults.level) }
    var fileName by remember(sources, currentDirectory) {
        val base = if (sources.size == 1) {
            val source = sources.single()
            if (source.isDirectory) source.name else source.nameWithoutExtension.ifBlank { source.name }
        } else currentDirectory.name.ifBlank { "Archive" }
        mutableStateOf(base + format.extension)
    }
    var password by remember { mutableStateOf("") }
    var splitCustom by remember { mutableStateOf(defaults.splitLengthMb > 0L) }
    var splitMb by remember { mutableStateOf(defaults.splitLengthMb.takeIf { it > 0 }?.toString().orEmpty()) }
    var each by remember { mutableStateOf(defaults.compressEachIndependently) }
    var deleteSources by remember { mutableStateOf(defaults.deleteSourcesAfterCompression) }
    var toOtherPane by remember { mutableStateOf(defaults.compressToOtherPane) }
    var formatMenu by remember { mutableStateOf(false) }
    var levelMenu by remember { mutableStateOf(false) }
    var splitMenu by remember { mutableStateOf(false) }

    fun updateExtension(newFormat: ArchiveFormat) {
        val oldExtension = ArchiveFormat.entries.firstOrNull { fileName.lowercase().endsWith(it.extension) }?.extension
        val base = if (oldExtension != null) fileName.dropLast(oldExtension.length) else fileName
        format = newFormat
        fileName = base + newFormat.extension
        if (newFormat !in setOf(ArchiveFormat.ZIP, ArchiveFormat.SEVEN_Z)) password = ""
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create archive", fontWeight = FontWeight.SemiBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = fileName,
                    onValueChange = { fileName = it },
                    label = { Text("Filename") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ArchiveDropdown(
                        label = "Format",
                        value = format.label,
                        expanded = formatMenu,
                        onExpandedChange = { formatMenu = it },
                        items = ArchiveFormat.entries.filter { it != ArchiveFormat.RAR }.map { it.label },
                        onSelect = { label -> updateExtension(ArchiveFormat.fromLabel(label)); formatMenu = false },
                        modifier = Modifier.weight(1f),
                    )
                    ArchiveDropdown(
                        label = "Level",
                        value = level.label,
                        expanded = levelMenu,
                        onExpandedChange = { levelMenu = it },
                        items = ArchiveLevel.entries.map { it.label },
                        onSelect = { level = ArchiveLevel.fromLabel(it); levelMenu = false },
                        modifier = Modifier.weight(1f),
                    )
                }

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    enabled = format == ArchiveFormat.ZIP || format == ArchiveFormat.SEVEN_Z,
                    label = { Text(if (format == ArchiveFormat.ZIP || format == ArchiveFormat.SEVEN_Z) "Password (no encryption if empty)" else "Password (ZIP/7z only)") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Bottom) {
                    ArchiveDropdown(
                        label = "Split length",
                        value = if (splitCustom) "Custom..." else "None",
                        expanded = splitMenu,
                        onExpandedChange = { splitMenu = it },
                        items = listOf("None", "Custom..."),
                        onSelect = {
                            splitCustom = it == "Custom..."
                            if (!splitCustom) splitMb = ""
                            splitMenu = false
                        },
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = splitMb,
                        onValueChange = { splitMb = it.filter(Char::isDigit) },
                        enabled = splitCustom,
                        label = { Text("MB") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.width(112.dp),
                    )
                }

                ArchiveSwitchRow("Compress each file/folder independently", each) { each = it }
                ArchiveSwitchRow("Delete source files after compression", deleteSources) { deleteSources = it }
                ArchiveSwitchRow(
                    "Compress to another window path\n${oppositeDirectory.absolutePath}",
                    toOtherPane,
                ) { toOtherPane = it }

                if (format == ArchiveFormat.GZIP || format == ArchiveFormat.XZ) {
                    Text(
                        "gzip/xz accepts one regular file. For folders or multiple selections choose tar.${if (format == ArchiveFormat.GZIP) "gz" else "xz"}.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val split = if (splitCustom) (splitMb.toLongOrNull() ?: 0L) else 0L
                    val saved = ArchiveDefaults(format, level, split, each, deleteSources, toOtherPane)
                    ArchiveSettings.save(context, saved)
                    onCreate(
                        ArchiveRequest(
                            sources = sources,
                            outputDirectory = if (toOtherPane) oppositeDirectory else currentDirectory,
                            fileName = fileName.trim(),
                            format = format,
                            level = level,
                            password = password,
                            splitLengthBytes = split * 1024L * 1024L,
                            compressEachIndependently = each,
                            deleteSourcesAfterCompression = deleteSources,
                        )
                    )
                },
                enabled = fileName.isNotBlank() && (!splitCustom || (splitMb.toLongOrNull() ?: 0L) > 0L),
            ) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("CANCEL") } },
    )
}

@Composable
private fun ArchiveDropdown(
    label: String,
    value: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    items: List<String>,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onExpandedChange(true) }
                .padding(vertical = 4.dp),
        ) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(3.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(value, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { onExpandedChange(false) }) {
            items.forEach { item -> DropdownMenuItem(text = { Text(item) }, onClick = { onSelect(item) }) }
        }
    }
}

@Composable
private fun ArchiveSwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onCheckedChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
