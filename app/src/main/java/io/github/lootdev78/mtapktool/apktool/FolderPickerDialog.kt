package io.github.lootdev78.mtapktool.apktool

import android.os.Environment
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.io.File

/**
 * Real filesystem folder picker for MTApktool.
 *
 * Apktool works with java.io.File paths, not SAF document Uris. Because MTApktool is a file
 * manager and requests MANAGE_EXTERNAL_STORAGE, using the same filesystem view here avoids
 * fragile Uri-to-path conversions and lets the user create the exact output directory.
 */
@Composable
fun ApktoolFolderPickerDialog(
    initialPath: String,
    title: String = "Ordner auswählen",
    onDismiss: () -> Unit,
    onSelected: (String) -> Unit,
) {
    val externalRoot = remember { Environment.getExternalStorageDirectory().absoluteFile }
    var current by remember(initialPath) {
        mutableStateOf(resolveStartDirectory(initialPath, externalRoot))
    }
    var pathText by remember(current.absolutePath) { mutableStateOf(current.absolutePath) }
    var folders by remember { mutableStateOf<List<File>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var showCreate by remember { mutableStateOf(false) }

    fun refresh() {
        val result = runCatching {
            current.listFiles()
                ?.asSequence()
                ?.filter { it.isDirectory && it.canRead() }
                ?.sortedBy { it.name.lowercase() }
                ?.toList()
                ?: emptyList()
        }
        folders = result.getOrDefault(emptyList())
        error = result.exceptionOrNull()?.message
        pathText = current.absolutePath
    }

    LaunchedEffect(current.absolutePath) { refresh() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = pathText,
                    onValueChange = { pathText = it },
                    label = { Text("Pfad") },
                    singleLine = true,
                    trailingIcon = {
                        IconButton(
                            onClick = {
                                val typed = File(pathText).absoluteFile
                                if (typed.isDirectory && typed.canRead()) {
                                    current = typed
                                    error = null
                                } else {
                                    error = "Ordner ist nicht lesbar: ${typed.absolutePath}"
                                }
                            },
                        ) {
                            Icon(Icons.Default.FolderOpen, contentDescription = "Pfad öffnen")
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )

                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(
                        enabled = current.parentFile != null,
                        onClick = { current.parentFile?.let { current = it } },
                    ) { Text("..") }
                    TextButton(onClick = { current = externalRoot }) { Text("SPEICHER") }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { showCreate = true }) {
                        Icon(Icons.Default.CreateNewFolder, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("NEUER ORDNER")
                    }
                }

                if (!error.isNullOrBlank()) {
                    Text(
                        error.orEmpty(),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                }

                LazyColumn(modifier = Modifier.heightIn(min = 180.dp, max = 430.dp)) {
                    items(folders, key = { it.absolutePath }) { folder ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { current = folder }
                                .padding(vertical = 9.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Default.Folder, contentDescription = null)
                            Spacer(Modifier.width(10.dp))
                            Text(
                                folder.name.ifBlank { folder.absolutePath },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("ABBRECHEN") } },
        confirmButton = {
            Button(onClick = { onSelected(current.absolutePath) }) { Text("AUSWÄHLEN") }
        },
    )

    if (showCreate) {
        CreateFolderDialog(
            parent = current,
            onDismiss = { showCreate = false },
            onCreated = { created ->
                showCreate = false
                current = created
            },
        )
    }
}

@Composable
private fun CreateFolderDialog(
    parent: File,
    onDismiss: () -> Unit,
    onCreated: (File) -> Unit,
) {
    var name by remember(parent.absolutePath) { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ordner erstellen") },
        text = {
            Column {
                Text(
                    parent.absolutePath,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        error = null
                    },
                    label = { Text("Ordnername") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                if (!error.isNullOrBlank()) {
                    Text(error.orEmpty(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("ABBRECHEN") } },
        confirmButton = {
            Button(
                enabled = name.trim().isNotEmpty() && '/' !in name && '\\' !in name,
                onClick = {
                    val child = File(parent, name.trim())
                    when {
                        child.isDirectory -> onCreated(child)
                        child.exists() -> error = "Es existiert bereits eine Datei mit diesem Namen."
                        child.mkdirs() -> onCreated(child)
                        else -> error = "Ordner konnte nicht erstellt werden."
                    }
                },
            ) { Text("ERSTELLEN") }
        },
    )
}

private fun resolveStartDirectory(path: String, fallback: File): File {
    val requested = File(path).absoluteFile
    if (requested.isDirectory && requested.canRead()) return requested
    val parent = requested.parentFile
    if (parent != null && parent.isDirectory && parent.canRead()) return parent
    return fallback.takeIf { it.isDirectory && it.canRead() } ?: File("/")
}
