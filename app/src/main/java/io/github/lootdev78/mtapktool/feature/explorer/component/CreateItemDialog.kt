package io.github.lootdev78.mtapktool.feature.explorer.component

import io.github.lootdev78.mtapktool.core.theme.MtClassicAlertDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun CreateItemDialog(
    onDismiss: () -> Unit,
    onCreate: (name: String, isFolder: Boolean) -> String? // Returns error msg if failed
) {
    var name by remember { mutableStateOf("") }
    var isFolder by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    MtClassicAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create New") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = isFolder, onClick = { isFolder = true })
                    Text("Folder", modifier = Modifier.padding(end = 16.dp))
                    RadioButton(selected = !isFolder, onClick = { isFolder = false })
                    Text("File")
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        errorMessage = null
                    },
                    label = { Text("Name") },
                    singleLine = true,
                    isError = errorMessage != null
                )
                errorMessage?.let {
                    Text(text = it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val trimmed = name.trim()
                    if (trimmed.isEmpty()) {
                        errorMessage = "Name cannot be empty!"
                    } else if (trimmed.contains("/") || trimmed.contains("\\")) {
                        errorMessage = "Invalid characters in name!"
                    } else {
                        val error = onCreate(trimmed, isFolder)
                        if (error != null) {
                            errorMessage = error
                        } else {
                            onDismiss()
                        }
                    }
                }
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}