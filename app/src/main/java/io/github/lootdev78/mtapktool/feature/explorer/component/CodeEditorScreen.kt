
package io.github.lootdev78.mtapktool.feature.editor

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun CodeEditorScreen(
    filePath: String,
    fileName: String,
    onBackClick: () -> Unit
) {
    var content by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(true) }
    var fileObject by remember { mutableStateOf<File?>(null) }

    LaunchedEffect(filePath) {
        val file = File(filePath)
        fileObject = file
        isLoading = true

        withContext(Dispatchers.IO) {
            runCatching {
                file.readText()
            }.onSuccess { loadedText ->
                withContext(Dispatchers.Main) {
                    content = loadedText
                    isLoading = false
                }
            }.onFailure { err ->
                withContext(Dispatchers.Main) {
                    content = "// Error loading file: ${err.localizedMessage}"
                    isLoading = false
                }
            }
        }
    }

    var isWordWrap by remember { mutableStateOf(true) }
    var showSearchPanel by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var showFileInfo by remember { mutableStateOf(false) }

    var searchQuery by remember { mutableStateOf("") }
    var isRegex by remember { mutableStateOf(false) }
    var isMatchCase by remember { mutableStateOf(false) }
    var showSearchMenu by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    if (showFileInfo && fileObject != null) {
        FileInfoDialog(
            file = fileObject!!,
            onDismiss = { showFileInfo = false }
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {

        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {

                IconButton(onClick = onBackClick) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = fileName,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }

                IconButton(
                    onClick = {
                        fileObject?.let { file ->
                            scope.launch(Dispatchers.IO) {
                                runCatching {
                                    file.writeText(content)
                                }.onSuccess {
                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(
                                            context,
                                            "Datei gespeichert",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }.onFailure { e ->
                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(
                                            context,
                                            "Fehler beim Speichern: ${e.localizedMessage}",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
                                }
                            }
                        }
                    }
                ) {
                    Icon(
                        Icons.Default.Save,
                        contentDescription = "Save",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(onClick = { /* Trigger Undo */ }) {
                    Icon(
                        Icons.AutoMirrored.Filled.Undo,
                        contentDescription = "Undo",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(onClick = { /* Trigger Redo */ }) {
                    Icon(
                        Icons.AutoMirrored.Filled.Redo,
                        contentDescription = "Redo",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(
                    onClick = {
                        showSearchPanel = !showSearchPanel
                    }
                ) {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = "Search",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = "More",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {

                        DropdownMenuItem(
                            text = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        io.github.lootdev78.mtapktool.core.i18n.UiText.auto(
                                            "Word Wrap"
                                        )
                                    )

                                    Spacer(modifier = Modifier.weight(1f))

                                    Checkbox(
                                        checked = isWordWrap,
                                        onCheckedChange = null
                                    )
                                }
                            },
                            onClick = {
                                isWordWrap = !isWordWrap
                                showMenu = false
                            }
                        )

                        DropdownMenuItem(
                            text = {
                                Text(
                                    io.github.lootdev78.mtapktool.core.i18n.UiText.auto(
                                        "File Info"
                                    )
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Info,
                                    contentDescription = null
                                )
                            },
                            onClick = {
                                showFileInfo = true
                                showMenu = false
                            }
                        )
                    }
                }
            }
        }

        val isDarkTheme = isSystemInDarkTheme()

        Box(
            modifier = Modifier
                .weight(1f)
                .background(
                    if (isDarkTheme) {
                        Color(0xFF1E1E1E)
                    } else {
                        Color.White
                    }
                ),
            contentAlignment = Alignment.Center
        ) {

            if (isLoading) {
                CircularProgressIndicator()
            } else {
                CodeTextEditor(
                    text = content,
                    onTextChanged = { content = it },
                    isWordWrap = isWordWrap,
                    searchQuery = searchQuery,
                    isRegex = isRegex,
                    isMatchCase = isMatchCase,
                    isDark = isDarkTheme
                )
            }
        }

        if (showSearchPanel) {

            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shadowElevation = 8.dp,
                modifier = Modifier.fillMaxWidth()
            ) {

                Column(
                    modifier = Modifier.padding(8.dp)
                ) {

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {

                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = {
                                Text(
                                    io.github.lootdev78.mtapktool.core.i18n.UiText.auto(
                                        "Find"
                                    )
                                )
                            },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        Box {

                            IconButton(
                                onClick = {
                                    showSearchMenu = true
                                }
                            ) {
                                Icon(
                                    Icons.Default.MoreVert,
                                    contentDescription = "Search Options"
                                )
                            }

                            DropdownMenu(
                                expanded = showSearchMenu,
                                onDismissRequest = {
                                    showSearchMenu = false
                                }
                            ) {

                                DropdownMenuItem(
                                    text = {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                io.github.lootdev78.mtapktool.core.i18n.UiText.auto(
                                                    "Regex"
                                                )
                                            )

                                            Spacer(
                                                modifier = Modifier.weight(1f)
                                            )

                                            Checkbox(
                                                checked = isRegex,
                                                onCheckedChange = {
                                                    isRegex = it
                                                }
                                            )
                                        }
                                    },
                                    onClick = {
                                        isRegex = !isRegex
                                    }
                                )

                                DropdownMenuItem(
                                    text = {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                io.github.lootdev78.mtapktool.core.i18n.UiText.auto(
                                                    "Match case"
                                                )
                                            )

                                            Spacer(
                                                modifier = Modifier.weight(1f)
                                            )

                                            Checkbox(
                                                checked = isMatchCase,
                                                onCheckedChange = {
                                                    isMatchCase = it
                                                }
                                            )
                                        }
                                    },
                                    onClick = {
                                        isMatchCase = !isMatchCase
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {

                        TextButton(
                            onClick = { /* Previous Match */ }
                        ) {
                            Text(
                                io.github.lootdev78.mtapktool.core.i18n.UiText.auto(
                                    "PREV"
                                )
                            )
                        }

                        TextButton(
                            onClick = { /* Next Match */ }
                        ) {
                            Text(
                                io.github.lootdev78.mtapktool.core.i18n.UiText.auto(
                                    "NEXT"
                                )
                            )
                        }

                        TextButton(
                            onClick = { /* Replace Current */ }
                        ) {
                            Text(
                                io.github.lootdev78.mtapktool.core.i18n.UiText.auto(
                                    "REP"
                                )
                            )
                        }

                        TextButton(
                            onClick = { /* Replace All */ }
                        ) {
                            Text(
                                io.github.lootdev78.mtapktool.core.i18n.UiText.auto(
                                    "ALL"
                                )
                            )
                        }

                        TextButton(
                            onClick = {
                                showSearchPanel = false
                            }
                        ) {
                            Text(
                                io.github.lootdev78.mtapktool.core.i18n.UiText.auto(
                                    "CLOSE"
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}
