package io.github.lootdev78.mtapktool.feature.explorer.component

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.lootdev78.mtapktool.core.theme.MtClassicAlertDialog
import io.github.lootdev78.mtapktool.feature.explorer.state.Bookmark
import io.github.lootdev78.mtapktool.feature.explorer.state.BookmarkStore

@Composable
fun BookmarkEditorDialog(
    bookmark: Bookmark,
    editingPath: String?,
    entries: List<Bookmark>,
    onDismiss: () -> Unit,
    onSave: (Bookmark) -> Unit,
) {
    var name by remember(bookmark) { mutableStateOf(bookmark.name) }
    var path by remember(bookmark) { mutableStateOf(bookmark.path) }
    var error by remember { mutableStateOf<String?>(null) }
    MtClassicAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (editingPath == null) "Lesezeichen hinzufügen" else "Lesezeichen bearbeiten") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(path, { path = it }, label = { Text("Pfad") }, minLines = 2, maxLines = 4, modifier = Modifier.fillMaxWidth())
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("ABBRECHEN") } },
        confirmButton = {
            TextButton(onClick = {
                runCatching {
                    val normalized = BookmarkStore.normalizePath(path)
                    require(name.isNotBlank()) { "Einen Namen angeben" }
                    require(entries.none { it.path != editingPath && it.path == normalized }) { "Für diesen Pfad gibt es bereits ein Lesezeichen" }
                    Bookmark(normalized, name.trim())
                }.onSuccess(onSave).onFailure { error = it.message }
            }) { Text("SPEICHERN") }
        },
    )
}

@Composable
fun BookmarkDeleteDialog(bookmark: Bookmark, onDismiss: () -> Unit, onDelete: () -> Unit) {
    MtClassicAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Lesezeichen löschen") },
        text = { Text(bookmark.name + "\n" + bookmark.path + "\n\nDer gespeicherte Verweis wird entfernt. Die Datei oder der Ordner bleibt erhalten.") },
        dismissButton = { TextButton(onClick = onDismiss) { Text("ABBRECHEN") } },
        confirmButton = { TextButton(onClick = onDelete) { Text("LÖSCHEN") } },
    )
}
