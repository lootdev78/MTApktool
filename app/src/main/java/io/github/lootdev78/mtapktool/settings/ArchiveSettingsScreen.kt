package io.github.lootdev78.mtapktool.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.lootdev78.mtapktool.archive.ArchiveSettings
import io.github.lootdev78.mtapktool.core.theme.MtClassicTopBar
import io.github.lootdev78.mtapktool.core.theme.MtClassicAlertDialog

@Composable
fun ArchiveSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var defaults by remember { mutableStateOf(ArchiveSettings.load(context)) }
    var packing by remember { mutableStateOf(false) }
    var extracting by remember { mutableStateOf(false) }
    Scaffold(topBar = {
        MtClassicTopBar(title = { Text("Archivierung") }, navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück") }
        })
    }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
            item { Text("Vorgaben", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 20.dp, bottom = 8.dp)) }
            item {
                ListItem(headlineContent = { Text("Verpacken") }, supportingContent = { Text("${defaults.format.label} · ${defaults.level.label} · ${if (defaults.compressToOtherPane) "anderes Panel" else "aktuelles Panel"}") }, trailingContent = {
                    TextButton(onClick = { packing = true }) { Text("OPTIONEN") }
                })
            }
            item {
                ListItem(headlineContent = { Text("Entpacken") }, supportingContent = { Text(if (defaults.extractToSubdirectory) "In einen Unterordner" else "Direkt in das Zielverzeichnis") }, trailingContent = {
                    TextButton(onClick = { extracting = true }) { Text("OPTIONEN") }
                })
            }
            item { HorizontalDivider(Modifier.padding(vertical = 12.dp)) }
            item { Text("Archive im Dateimanager", style = MaterialTheme.typography.titleMedium) }
            item { Text("Archive öffnen sich im aktiven Panel. Beide Panels verwenden denselben Arbeitsstand, wenn sie dasselbe Archiv anzeigen. Mit „Archiv aktualisieren“ werden Änderungen geprüft und gespeichert; beim Verlassen kannst du Speichern, Verwerfen oder Abbrechen wählen.", modifier = Modifier.padding(vertical = 12.dp), style = MaterialTheme.typography.bodyMedium) }
            item { Text("Passwörter und Split-Größe werden im jeweiligen Verpacken-/Entpacken-Dialog gewählt.", style = MaterialTheme.typography.bodyMedium) }
            item { Text("FTP und Android-Dokumentanbieter: Nach Änderungen wird das Zurückschreiben zur Originaldatei angeboten. Eine zwischenzeitlich extern geänderte Originaldatei wird erkannt.", modifier = Modifier.padding(vertical = 12.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
    if (packing) ArchiveDefaultsDialog {
        packing = false; defaults = ArchiveSettings.load(context)
    }
    if (extracting) {
        var draft by remember { mutableStateOf(defaults) }
        MtClassicAlertDialog(onDismissRequest = { extracting = false }, title = { Text("Entpacken – Vorgaben") }, text = {
            Column {
                ArchiveToggle("In einen Unterordner entpacken", draft.extractToSubdirectory) { draft = draft.copy(extractToSubdirectory = it) }
                ArchiveToggle("In das andere Panel entpacken", draft.extractToOtherPane) { draft = draft.copy(extractToOtherPane = it) }
                ArchiveToggle("Originalarchiv nach Erfolg löschen", draft.deleteSourceAfterExtraction) { draft = draft.copy(deleteSourceAfterExtraction = it) }
            }
        }, dismissButton = { TextButton(onClick = { extracting = false }) { Text("ABBRECHEN") } }, confirmButton = {
            TextButton(onClick = { ArchiveSettings.save(context, draft); defaults = draft; extracting = false }) { Text("SPEICHERN") }
        })
    }
}

@Composable
private fun ArchiveToggle(title: String, value: Boolean, set: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Checkbox(value, set); Text(title, Modifier.weight(1f))
    }
}
