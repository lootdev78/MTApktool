package io.github.lootdev78.mtapktool.archive

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.lootdev78.mtapktool.core.theme.MtClassicAlertDialog
import io.github.lootdev78.mtapktool.feature.explorer.model.formatSize

@Composable
fun ArchiveCharsetDialog(archiveName: String, current: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var selected by remember(archiveName, current) { mutableStateOf(current) }
    MtClassicAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Archiv-Zeichensatz") },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                Text(archiveName, style = MaterialTheme.typography.titleSmall)
                Text("Dateinamen mit diesem Zeichensatz neu laden. Dateien im Editor und Archivänderungen zuerst speichern; offene Unterarchive zuerst schließen.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 8.dp))
                ArchiveCharsets.choices.forEach { choice ->
                    Row(Modifier.fillMaxWidth().clickable { selected = choice.value }, verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected == choice.value, onClick = { selected = choice.value })
                        Text(choice.label)
                    }
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("ABBRECHEN") } },
        confirmButton = { TextButton(onClick = { onConfirm(selected) }) { Text("NEU LADEN") } },
    )
}

@Composable
fun ArchiveTestResultDialog(report: ArchiveTestReport, onDismiss: () -> Unit) {
    MtClassicAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (report.error == null) "Archivtest erfolgreich" else "Archivtest fehlgeschlagen") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(report.archiveName, style = MaterialTheme.typography.titleSmall)
                report.result?.let { result ->
                    Text(result.entries.toString() + " Einträge vollständig gelesen • " + formatSize(result.bytesRead))
                    Text(result.checks, style = MaterialTheme.typography.bodySmall)
                }
                report.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } },
    )
}
