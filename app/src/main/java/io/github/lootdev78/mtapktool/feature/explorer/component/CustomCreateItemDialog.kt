package io.github.lootdev78.mtapktool.feature.explorer.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/** Compact MT-style create dialog: one name field and explicit file/folder actions. */
@Composable
fun CustomCreateItemDialog(
    onDismiss: () -> Unit,
    onCreate: (name: String, isFolder: Boolean) -> Unit,
) {
    var nameInput by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val validName = nameInput.trim().isNotEmpty()

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            shape = RoundedCornerShape(2.dp),
            color = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            shadowElevation = 8.dp,
            modifier = Modifier.fillMaxWidth(0.90f).widthIn(max = 420.dp),
        ) {
            Column(Modifier.padding(start = 24.dp, top = 22.dp, end = 16.dp, bottom = 8.dp)) {
                Text(
                    text = "Create",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.height(14.dp))
                OutlinedTextField(
                    value = nameInput,
                    onValueChange = { nameInput = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                )
                Spacer(Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) { Text("CANCEL") }
                    TextButton(
                        enabled = validName,
                        onClick = { onCreate(nameInput.trim(), false) },
                    ) { Text("FILE") }
                    TextButton(
                        enabled = validName,
                        onClick = { onCreate(nameInput.trim(), true) },
                    ) { Text("FOLDER") }
                }
            }
        }
    }
}
