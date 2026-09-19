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
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/** MT-style rename workflow with the basename selected while keeping the extension intact. */
@Composable
fun RenameDialog(
    initialName: String,
    onDismiss: () -> Unit,
    onRename: (String) -> Unit,
) {
    val selectionEnd = initialName.lastIndexOf('.')
        .takeIf { it > 0 && it < initialName.lastIndex }
        ?: initialName.length
    var value by remember(initialName) {
        mutableStateOf(TextFieldValue(initialName, selection = TextRange(0, selectionEnd)))
    }
    val focusRequester = remember { FocusRequester() }
    val cleanName = value.text.trim()
    val canRename = cleanName.isNotEmpty() && cleanName != initialName

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
                Text("Rename", fontSize = 24.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(14.dp))
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    label = { Text("New name") },
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
                        enabled = canRename,
                        onClick = { onRename(cleanName) },
                    ) { Text("RENAME") }
                }
            }
        }
    }
}
