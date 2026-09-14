package io.github.lootdev78.mtapktool.archive

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.io.File

@Composable
fun ArchiveActionDialog(
    archive: File,
    onDismiss: () -> Unit,
    onOpen: (password: String) -> Unit,
    onExtract: (password: String) -> Unit,
) {
    val format = ArchiveFormat.fromFile(archive)
    var password by remember(archive.absolutePath) { mutableStateOf("") }
    val passwordCapable = format == ArchiveFormat.ZIP || format == ArchiveFormat.SEVEN_Z

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(archive.name, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            Column {
                Text(
                    "${format?.label ?: "Archiv"} • ${formatBytes(archive.length())}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "Öffnen zeigt den Inhalt schreibgeschützt im aktuell verwendeten Panel. Dateien werden per Kopieren/Entpacken in einen normalen Ordner herausgeholt; das Archiv selbst wird nicht verändert.",
                    modifier = Modifier.padding(top = 10.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (passwordCapable) {
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Passwort (falls erforderlich)") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    )
                }
            }
        },
        dismissButton = {
            TextButton(onClick = { onExtract(password) }) { Text("ENTPACKEN") }
        },
        confirmButton = {
            TextButton(onClick = { onOpen(password) }) { Text("ANZEIGEN") }
        },
    )
}

@Composable
fun ArchiveExtractDialog(
    archive: File,
    currentDirectory: File,
    oppositeDirectory: File,
    initialPassword: String = "",
    onDismiss: () -> Unit,
    onExtract: (ArchiveExtractRequest) -> Unit,
) {
    var useSubdirectory by remember(archive.absolutePath) { mutableStateOf(true) }
    var relativePath by remember(archive.absolutePath) { mutableStateOf(defaultExtractFolderName(archive)) }
    var toOtherPane by remember(archive.absolutePath) { mutableStateOf(false) }
    var deleteSource by remember(archive.absolutePath) { mutableStateOf(false) }
    var password by remember(archive.absolutePath) { mutableStateOf(initialPassword) }
    val format = ArchiveFormat.fromFile(archive)
    val passwordCapable = format == ArchiveFormat.ZIP || format == ArchiveFormat.SEVEN_Z

    val baseDirectory = if (toOtherPane) oppositeDirectory else currentDirectory
    val resolvedDirectory = if (useSubdirectory) {
        val safeRelative = normalizeRelativePath(relativePath)
        File(baseDirectory, safeRelative.ifBlank { defaultExtractFolderName(archive) })
    } else {
        baseDirectory
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Extract") },
        text = {
            Column {
                ExtractRadioRow(
                    label = "Extract to current directory",
                    selected = !useSubdirectory,
                    onClick = { useSubdirectory = false },
                )
                ExtractRadioRow(
                    label = "Path to extract to...",
                    selected = useSubdirectory,
                    onClick = { useSubdirectory = true },
                )
                OutlinedTextField(
                    value = relativePath,
                    onValueChange = { relativePath = it },
                    enabled = useSubdirectory,
                    singleLine = true,
                    label = { Text("Relative path") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    resolvedDirectory.absolutePath,
                    modifier = Modifier.padding(top = 4.dp, bottom = 6.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                ExtractCheckRow(
                    label = "Extract to another window path\n${oppositeDirectory.absolutePath}",
                    checked = toOtherPane,
                    onChecked = { toOtherPane = it },
                )
                ExtractCheckRow(
                    label = "Delete source file after extraction",
                    checked = deleteSource,
                    onChecked = { deleteSource = it },
                )
                if (passwordCapable) {
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Password (if required)") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("CLOSE") } },
        confirmButton = {
            TextButton(
                enabled = !useSubdirectory || relativePath.trim().isNotEmpty(),
                onClick = {
                    onExtract(
                        ArchiveExtractRequest(
                            archive = archive,
                            outputDirectory = resolvedDirectory,
                            password = password,
                            deleteSourceAfterExtraction = deleteSource,
                        )
                    )
                },
            ) { Text("OK") }
        },
    )
}

@Composable
private fun ExtractRadioRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Spacer(Modifier.width(4.dp))
        Text(label)
    }
}

@Composable
private fun ExtractCheckRow(label: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onChecked(!checked) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = onChecked)
        Spacer(Modifier.width(4.dp))
        Text(label, modifier = Modifier.weight(1f))
    }
}

private fun normalizeRelativePath(value: String): String = value
    .replace('\\', '/')
    .split('/')
    .filter { it.isNotBlank() && it != "." && it != ".." }
    .joinToString(File.separator)

private fun defaultExtractFolderName(file: File): String {
    val format = ArchiveFormat.fromFile(file)
    return if (format != null) file.name.dropLast(format.extension.length).ifBlank { file.nameWithoutExtension }
    else file.nameWithoutExtension.ifBlank { "Extracted" }
}

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024L) return "$bytes B"
    val units = arrayOf("KB", "MB", "GB", "TB")
    var value = bytes.toDouble() / 1024.0
    var index = 0
    while (value >= 1024.0 && index < units.lastIndex) {
        value /= 1024.0
        index++
    }
    return String.format(java.util.Locale.US, "%.1f %s", value, units[index])
}
