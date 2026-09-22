package io.github.lootdev78.mtapktool.feature.explorer.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.lootdev78.mtapktool.core.theme.MtClassicAlertDialog
import io.github.lootdev78.mtapktool.feature.explorer.state.FileFilter
import io.github.lootdev78.mtapktool.feature.explorer.state.SortField
import io.github.lootdev78.mtapktool.feature.explorer.state.SortSpec
import java.io.File

@Composable
fun HiddenFilesDialog(
    showSystemHidden: Boolean,
    showManuallyHidden: Boolean,
    selectedCount: Int,
    manualHiddenCount: Int,
    onShowSystemHidden: (Boolean) -> Unit,
    onShowManuallyHidden: (Boolean) -> Unit,
    onHideSelected: () -> Unit,
    onEditHidden: () -> Unit,
    onDismiss: () -> Unit,
) {
    MtClassicAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Versteckte Dateien") },
        text = {
            Column {
                HiddenToggleRow(Icons.Default.Visibility, "Systemdateien anzeigen", showSystemHidden, onShowSystemHidden)
                HiddenToggleRow(Icons.Default.Visibility, "Manuell versteckte anzeigen", showManuallyHidden, onShowManuallyHidden)
                HorizontalDivider(Modifier.padding(vertical = 6.dp))
                ActionRow(
                    icon = Icons.Default.VisibilityOff,
                    label = if (selectedCount > 0) "Ausgewählte Dateien ausblenden ($selectedCount)" else "Ausgewählte Dateien ausblenden",
                    enabled = selectedCount > 0,
                    onClick = {
                        onHideSelected()
                        onDismiss()
                    },
                )
                ActionRow(
                    icon = Icons.Default.Edit,
                    label = if (manualHiddenCount > 0) "Versteckte Dateien bearbeiten ($manualHiddenCount)" else "Versteckte Dateien bearbeiten",
                    enabled = manualHiddenCount > 0,
                    onClick = onEditHidden,
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("SCHLIESSEN") } },
    )
}

@Composable
private fun HiddenToggleRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clickable { onCheckedChange(!checked) }.padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null)
        Spacer(Modifier.width(12.dp))
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun ActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onClick).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            label,
            style = MaterialTheme.typography.titleMedium,
            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
        )
    }
}

@Composable
fun EditHiddenFilesDialog(
    paths: List<String>,
    onRemove: (String) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    MtClassicAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Manuell versteckte Dateien") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                paths.forEach { path ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onRemove(path) }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Default.Visibility, contentDescription = null)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(File(path).name.ifBlank { path }, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(path, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        },
        dismissButton = {
            if (paths.isNotEmpty()) TextButton(onClick = onClear) { Text("ALLE EINBLENDEN") }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("FERTIG") } },
    )
}

@Composable
fun SortFilesDialog(
    windowLabel: String,
    current: SortSpec,
    onManage: () -> Unit,
    onApply: (SortSpec, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var field by remember(current) { mutableStateOf(current.field) }
    var descending by remember(current) { mutableStateOf(current.descending) }
    var onlyFolder by remember { mutableStateOf(false) }

    MtClassicAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sortieren – $windowLabel") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(Modifier.fillMaxWidth()) {
                    SortChoice("Nach Name", SortField.NAME, field, { field = it }, Modifier.weight(1f))
                    SortChoice("Nach Größe", SortField.SIZE, field, { field = it }, Modifier.weight(1f))
                }
                Row(Modifier.fillMaxWidth()) {
                    SortChoice("Nach Datum", SortField.DATE, field, { field = it }, Modifier.weight(1f))
                    SortChoice("Nach Typ", SortField.TYPE, field, { field = it }, Modifier.weight(1f))
                }
                CheckRow("Nur auf diesen Ordner anwenden", onlyFolder) { onlyFolder = it }
                CheckRow("Reihenfolge umkehren", descending) { descending = it }
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onManage) { Text("VERWALTEN") }
                TextButton(onClick = onDismiss) { Text("ABBRECHEN") }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onApply(SortSpec(field, descending), onlyFolder)
                onDismiss()
            }) { Text("OK") }
        },
    )
}

@Composable
private fun SortChoice(
    label: String,
    value: SortField,
    selected: SortField,
    onSelect: (SortField) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier.clickable { onSelect(value) }.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = selected == value, onClick = { onSelect(value) })
        Text(label)
    }
}

@Composable
private fun CheckRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!checked) }, verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = onChange)
        Text(label)
    }
}

@Composable
fun FileFilterDialog(
    current: FileFilter,
    onApply: (FileFilter) -> Unit,
    onDismiss: () -> Unit,
) {
    MtClassicAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Filter") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                FileFilter.entries.forEach { filter ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onApply(filter); onDismiss() }.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = current == filter, onClick = { onApply(filter); onDismiss() })
                        Text(fileFilterLabel(filter))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("SCHLIESSEN") } },
    )
}

fun fileFilterLabel(filter: FileFilter): String = when (filter) {
    FileFilter.ALL -> "Alle Dateien"
    FileFilter.FOLDERS -> "Nur Ordner"
    FileFilter.FILES -> "Nur Dateien"
    FileFilter.APK -> "APK / App-Pakete"
    FileFilter.ARCHIVE -> "Archive"
    FileFilter.IMAGE -> "Bilder"
    FileFilter.AUDIO -> "Audio"
    FileFilter.VIDEO -> "Video"
    FileFilter.DOCUMENT -> "Dokumente / Text"
    FileFilter.RECENT -> "Zuletzt geändert (1 Stunde)"
}
