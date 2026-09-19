package io.github.lootdev78.mtapktool.feature.explorer.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.launch

@Composable
fun GoToPathDialog(
    initialPath: String,
    onDismiss: () -> Unit,
    onGo: (String) -> Unit,
) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }
    var pathValue by remember(initialPath) {
        mutableStateOf(TextFieldValue(initialPath, TextRange(0, initialPath.length)))
    }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(4.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 8.dp,
            modifier = Modifier.width(420.dp),
        ) {
            Column(Modifier.padding(start = 28.dp, top = 24.dp, end = 20.dp, bottom = 12.dp)) {
                Text(io.github.lootdev78.mtapktool.core.i18n.UiText.auto("Jump to path"), fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
                TextField(
                    value = pathValue,
                    onValueChange = { pathValue = it },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                        cursorColor = MaterialTheme.colorScheme.primary,
                    ),
                    modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                )
                Spacer(Modifier.height(20.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(
                        onClick = {
                            scope.launch {
                                val text = clipboard.getClipEntry()?.clipData?.getItemAt(0)?.text?.toString().orEmpty()
                                if (text.isNotBlank()) pathValue = TextFieldValue(text, TextRange(0, text.length))
                            }
                        },
                        contentPadding = PaddingValues(horizontal = 12.dp),
                    ) { Text(io.github.lootdev78.mtapktool.core.i18n.UiText.auto("PASTE"), fontWeight = FontWeight.Bold) }
                    Row {
                        TextButton(onClick = onDismiss, contentPadding = PaddingValues(horizontal = 12.dp)) {
                            Text(io.github.lootdev78.mtapktool.core.i18n.UiText.auto("CANCEL"), fontWeight = FontWeight.Bold)
                        }
                        TextButton(
                            onClick = { pathValue.text.trim().takeIf(String::isNotBlank)?.let(onGo) },
                            contentPadding = PaddingValues(horizontal = 12.dp),
                        ) { Text(io.github.lootdev78.mtapktool.core.i18n.UiText.auto("OK"), fontWeight = FontWeight.Bold) }
                    }
                }
            }
        }
    }
}
