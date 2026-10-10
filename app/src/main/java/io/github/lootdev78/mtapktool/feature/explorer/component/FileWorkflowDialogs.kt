package io.github.lootdev78.mtapktool.feature.explorer.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import io.github.lootdev78.mtapktool.core.theme.MtClassicAlertDialog
import io.github.lootdev78.mtapktool.feature.explorer.state.RenamePreview
import io.github.lootdev78.mtapktool.feature.explorer.util.FileWorkflow
import kotlinx.coroutines.launch

@Composable
fun AdvancedSearchDialog(
    directory: String,
    history: List<String>,
    onClearHistory: () -> Unit,
    onSearch: (FileWorkflow.SearchSpec) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var recursive by remember { mutableStateOf(false) }
    var advanced by remember { mutableStateOf(false) }
    var matchCase by remember { mutableStateOf(false) }
    var regex by remember { mutableStateOf(false) }
    var content by remember { mutableStateOf("") }
    var minimum by remember { mutableStateOf("") }
    var maximum by remember { mutableStateOf("") }
    var showHistory by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    MtClassicAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Suchen") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(directory, style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(query, { query = it; error = null }, label = { Text("Dateiname / Suchbegriff") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Box {
                    TextButton(onClick = { showHistory = true }, enabled = history.isNotEmpty()) { Text("SUCHVERLAUF ▾") }
                    DropdownMenu(showHistory, { showHistory = false }) {
                        history.forEach { value -> DropdownMenuItem(text = { Text(value) }, onClick = { query = value; showHistory = false }) }
                        HorizontalDivider()
                        DropdownMenuItem(text = { Text("Verlauf löschen") }, onClick = { onClearHistory(); showHistory = false })
                    }
                }
                WorkflowCheck("Unterordner durchsuchen", recursive) { recursive = it }
                TextButton(onClick = { advanced = !advanced }) { Text(if (advanced) "ERWEITERTE SUCHE ▴" else "ERWEITERTE SUCHE ▾") }
                if (advanced) {
                    WorkflowCheck("Groß-/Kleinschreibung beachten", matchCase) { matchCase = it }
                    WorkflowCheck("Regulärer Ausdruck für Dateinamen", regex) { regex = it }
                    OutlinedTextField(content, { content = it }, label = { Text("Text innerhalb der Datei") }, modifier = Modifier.fillMaxWidth())
                    Text("Dateigröße in Bytes", style = MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(minimum, { minimum = it }, label = { Text("Minimum") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.weight(1f))
                        OutlinedTextField(maximum, { maximum = it }, label = { Text("Maximum") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.weight(1f))
                    }
                    Text("Textsuche: UTF-8, Dateien bis 10 MiB. Nicht lesbare Dateien und erreichte Limits werden im Ergebnis angezeigt.", style = MaterialTheme.typography.bodySmall)
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("ABBRECHEN") } },
        confirmButton = { TextButton(onClick = {
            runCatching {
                FileWorkflow.SearchSpec(query, recursive, matchCase, regex, content,
                    if (minimum.isBlank()) -1 else minimum.toLong(), if (maximum.isBlank()) -1 else maximum.toLong())
            }.onSuccess { onSearch(it); onDismiss() }.onFailure { error = it.message ?: "Ungültige Suchoptionen" }
        }) { Text("SUCHEN") } },
    )
}

@Composable
fun MultiRenameDialog(
    count: Int,
    onPreview: suspend (FileWorkflow.RenameSpec) -> RenamePreview,
    onRename: (RenamePreview) -> Unit,
    onDismiss: () -> Unit,
) {
    var template by remember { mutableStateOf(TextFieldValue("{P}{S}")) }
    var find by remember { mutableStateOf("") }
    var replacement by remember { mutableStateOf("") }
    var regex by remember { mutableStateOf(false) }
    var matchCase by remember { mutableStateOf(false) }
    var preview by remember { mutableStateOf<RenamePreview?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    fun buildPreview() {
        busy = true; error = null
        scope.launch {
            runCatching { onPreview(FileWorkflow.RenameSpec(template.text, find, replacement, regex, matchCase)) }
                .onSuccess { preview = it }.onFailure { error = it.message ?: "Vorschau fehlgeschlagen" }
            busy = false
        }
    }
    MtClassicAlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(if (preview == null) "$count Dateien umbenennen" else "Umbenennen – Vorschau") },
        text = {
            if (preview != null) {
                Column {
                    Text("Kollisionen bekommen einen Nummernzusatz. Unveränderte Namen bleiben erhalten.", style = MaterialTheme.typography.bodySmall)
                    LazyColumn(Modifier.heightIn(max = 360.dp)) {
                        items(preview!!.entries, key = { it.id }) { entry ->
                            Column(Modifier.padding(vertical = 8.dp)) {
                                Text(entry.original)
                                Text("→ ${entry.target}", color = if (entry.unchanged()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            } else {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("{P}: Name ohne Erweiterung · {S}: Erweiterung · {0}: Nummer ab 0 · {z0}: Nummer mit führenden Nullen", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(template, { template = it }, label = { Text("Namensvorlage") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        listOf("{P}", "{S}", "{0}", "{z0}").forEach { token -> TextButton(onClick = {
                            val a = template.selection.min; val b = template.selection.max
                            template = TextFieldValue(template.text.replaceRange(a, b, token), TextRange(a + token.length))
                        }) { Text(token) } }
                    }
                    OutlinedTextField(find, { find = it }, label = { Text("Suchen") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(replacement, { replacement = it }, label = { Text("Ersetzen durch") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    WorkflowCheck("Regulärer Ausdruck", regex) { regex = it }
                    WorkflowCheck("Groß-/Kleinschreibung beachten", matchCase) { matchCase = it }
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                }
            }
        },
        dismissButton = {
            Row {
                if (preview != null) TextButton(onClick = { preview = null }) { Text("BEARBEITEN") }
                TextButton(onClick = onDismiss, enabled = !busy) { Text("ABBRECHEN") }
            }
        },
        confirmButton = {
            TextButton(enabled = !busy, onClick = {
                val planned = preview
                if (planned == null) buildPreview() else { onRename(planned); onDismiss() }
            }) { Text(if (preview == null) "VORSCHAU" else "UMBENENNEN") }
        },
    )
}

@Composable
private fun WorkflowCheck(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!checked) }, verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked, onChange)
        Text(label, modifier = Modifier.weight(1f))
    }
}
