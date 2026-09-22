package io.github.lootdev78.mtapktool.feature.editor

import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.lootdev78.mtapktool.R
import io.github.lootdev78.mtapktool.apktool.ApktoolProjectSessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.ArrayDeque

private data class EditorMatch(val start: Int, val end: Int)

@Composable
fun CodeEditorScreen(
    filePath: String,
    fileName: String,
    onBackClick: () -> Unit,
    onOpenFile: (String, String) -> Unit = { _, _ -> },
) {
    val context = LocalContext.current
    remember(context) { ApktoolProjectSessionManager.configure(context.filesDir); true }
    var content by remember(filePath) { mutableStateOf("") }
    var lastSavedContent by remember(filePath) { mutableStateOf("") }
    var isLoading by remember(filePath) { mutableStateOf(true) }
    var fileObject by remember(filePath) { mutableStateOf<File?>(null) }
    val undo = remember(filePath) { ArrayDeque<String>() }
    val redo = remember(filePath) { ArrayDeque<String>() }

    LaunchedEffect(filePath) {
        isLoading = true
        val result = withContext(Dispatchers.IO) {
            runCatching {
                if (filePath.startsWith("content://")) {
                    fileObject = null
                    context.contentResolver.openInputStream(Uri.parse(filePath))?.bufferedReader()?.use { it.readText() }
                        ?: error("Cannot open document")
                } else {
                    File(filePath).also { fileObject = it }.readText()
                }
            }
        }
        result.onSuccess { content = it; lastSavedContent = it; undo.clear(); redo.clear() }
            .onFailure { content = "// Error loading file: ${it.localizedMessage ?: it.javaClass.simpleName}" }
        isLoading = false
    }

    var isWordWrap by remember { mutableStateOf(true) }
    var showSearchPanel by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var showFileInfo by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var replacement by remember { mutableStateOf("") }
    var isRegex by remember { mutableStateOf(false) }
    var isMatchCase by remember { mutableStateOf(false) }
    var showSearchMenu by remember { mutableStateOf(false) }
    var activeMatch by remember(searchQuery, isRegex, isMatchCase, content) { mutableIntStateOf(-1) }
    var selectionToken by remember { mutableLongStateOf(0L) }
    var selection by remember { mutableStateOf<EditorSelectionRequest?>(null) }
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val recentPrefs = remember(context) { context.getSharedPreferences("editor_recent_files", android.content.Context.MODE_PRIVATE) }
    var recentFiles by remember(filePath) {
        mutableStateOf(
            recentPrefs.getString("paths", "").orEmpty().lineSequence().filter { it.isNotBlank() }.toList()
        )
    }
    val siblingFiles = remember(filePath) {
        if (filePath.startsWith("content://")) emptyList() else {
            File(filePath).parentFile?.listFiles { candidate ->
                candidate.isFile && candidate.extension.lowercase() in setOf(
                    "txt", "xml", "json", "json5", "yml", "yaml", "properties", "gradle", "kts", "kt", "java",
                    "smali", "html", "htm", "css", "js", "ts", "md", "csv", "ini", "cfg", "conf", "log", "pro"
                )
            }?.sortedBy { it.name.lowercase() }.orEmpty()
        }
    }

    LaunchedEffect(filePath) {
        if (filePath.isNotBlank()) {
            val updated = (listOf(filePath) + recentFiles.filterNot { it == filePath }).take(20)
            recentFiles = updated
            recentPrefs.edit().putString("paths", updated.joinToString("\n")).apply()
        }
    }

    val matches = remember(content, searchQuery, isRegex, isMatchCase) { findMatches(content, searchQuery, isRegex, isMatchCase) }

    fun recordChange(next: String) {
        if (next == content) return
        if (undo.isEmpty() || undo.peekLast() != content) {
            undo.addLast(content)
            while (undo.size > 100) undo.removeFirst()
        }
        redo.clear()
        content = next
    }

    fun selectMatch(index: Int) {
        if (matches.isEmpty()) return
        val normalized = ((index % matches.size) + matches.size) % matches.size
        activeMatch = normalized
        val match = matches[normalized]
        selectionToken++
        selection = EditorSelectionRequest(match.start, match.end, selectionToken)
    }

    fun replaceCurrent(all: Boolean) {
        if (matches.isEmpty()) return
        val next = if (all) replaceAllMatches(content, searchQuery, replacement, isRegex, isMatchCase)
        else {
            val m = matches[if (activeMatch in matches.indices) activeMatch else 0]
            content.substring(0, m.start) + replacementForMatch(content.substring(m.start, m.end), searchQuery, replacement, isRegex, isMatchCase) + content.substring(m.end)
        }
        recordChange(next)
        activeMatch = -1
    }

    if (showFileInfo && fileObject != null) FileInfoDialog(fileObject!!, onDismiss = { showFileInfo = false })

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(modifier = Modifier.widthIn(max = 330.dp)) {
                Text("Dateien", fontSize = 20.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(16.dp))
                HorizontalDivider()
                if (siblingFiles.isNotEmpty()) {
                    Text("Aktueller Ordner", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp))
                    siblingFiles.take(80).forEach { candidate ->
                        NavigationDrawerItem(
                            label = { Text(candidate.name, maxLines = 1) },
                            selected = candidate.absolutePath == filePath,
                            onClick = {
                                if (content != lastSavedContent) {
                                    Toast.makeText(context, "Datei zuerst speichern", Toast.LENGTH_SHORT).show()
                                } else {
                                    scope.launch { drawerState.close() }
                                    onOpenFile(candidate.absolutePath, candidate.name)
                                }
                            },
                            modifier = Modifier.padding(horizontal = 8.dp),
                        )
                    }
                }
                if (recentFiles.isNotEmpty()) {
                    HorizontalDivider(Modifier.padding(top = 8.dp))
                    Text("Zuletzt geöffnet", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp))
                    recentFiles.take(20).forEach { recent ->
                        val label = if (recent.startsWith("content://")) Uri.parse(recent).lastPathSegment.orEmpty().ifBlank { recent } else File(recent).name.ifBlank { recent }
                        NavigationDrawerItem(
                            label = { Text(label, maxLines = 1) },
                            selected = recent == filePath,
                            onClick = {
                                if (content != lastSavedContent) {
                                    Toast.makeText(context, "Datei zuerst speichern", Toast.LENGTH_SHORT).show()
                                } else {
                                    scope.launch { drawerState.close() }
                                    onOpenFile(recent, label)
                                }
                            },
                            modifier = Modifier.padding(horizontal = 8.dp),
                        )
                    }
                }
            }
        },
    ) {
    Column(Modifier.fillMaxSize()) {
        Surface(color = MaterialTheme.colorScheme.surfaceVariant, shadowElevation = 2.dp) {
            Row(Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBackClick) { Icon(painterResource(R.drawable.mt_ic_back), "Back") }
                IconButton(onClick = { scope.launch { drawerState.open() } }) { Icon(Icons.Default.Menu, "Dateien") }
                Text(if (content != lastSavedContent) "$fileName *" else fileName, Modifier.weight(1f), fontSize = 16.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                IconButton(onClick = {
                    scope.launch(Dispatchers.IO) {
                        runCatching {
                            if (filePath.startsWith("content://")) {
                                context.contentResolver.openOutputStream(Uri.parse(filePath), "wt")?.bufferedWriter()?.use { it.write(content) }
                                    ?: error("Cannot write document")
                            } else {
                                val target = fileObject ?: File(filePath)
                                target.writeText(content)
                                ApktoolProjectSessionManager.markFileChanged(target)
                            }
                        }.onSuccess {
                            withContext(Dispatchers.Main) { lastSavedContent = content; Toast.makeText(context, "File saved", Toast.LENGTH_SHORT).show() }
                        }.onFailure { e ->
                            withContext(Dispatchers.Main) { Toast.makeText(context, "Error saving: ${e.localizedMessage}", Toast.LENGTH_LONG).show() }
                        }
                    }
                }) { Icon(painterResource(R.drawable.mt_ic_save), "Save") }
                IconButton(enabled = undo.isNotEmpty(), onClick = { if (undo.isNotEmpty()) { redo.addLast(content); content = undo.removeLast() } }) { Icon(painterResource(R.drawable.mt_ic_undo), "Undo") }
                IconButton(enabled = redo.isNotEmpty(), onClick = { if (redo.isNotEmpty()) { undo.addLast(content); content = redo.removeLast() } }) { Icon(painterResource(R.drawable.mt_ic_redo), "Redo") }
                IconButton(onClick = { showSearchPanel = !showSearchPanel }) { Icon(painterResource(R.drawable.mt_ic_search), "Search") }
                Box {
                    IconButton(onClick = { showMenu = true }) { Icon(painterResource(R.drawable.mt_ic_more), "More") }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(text = { Row(verticalAlignment = Alignment.CenterVertically) { Text("Word Wrap"); Spacer(Modifier.weight(1f)); Checkbox(isWordWrap, null) } }, onClick = { isWordWrap = !isWordWrap; showMenu = false })
                        if (fileObject != null) DropdownMenuItem(text = { Text("File Info") }, leadingIcon = { Icon(painterResource(R.drawable.mt_ic_info), null) }, onClick = { showFileInfo = true; showMenu = false })
                    }
                }
            }
        }

        val dark = isSystemInDarkTheme()
        Box(Modifier.weight(1f).background(if (dark) Color(0xFF1E1E1E) else Color.White), contentAlignment = Alignment.Center) {
            if (isLoading) CircularProgressIndicator()
            else CodeTextEditor(content, ::recordChange, isWordWrap, searchQuery, isRegex, isMatchCase, dark, selection)
        }

        if (showSearchPanel) {
            Surface(color = MaterialTheme.colorScheme.surfaceVariant, shadowElevation = 2.dp) {
                Column(Modifier.fillMaxWidth().padding(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(searchQuery, { searchQuery = it; activeMatch = -1 }, placeholder = { Text("Find") }, singleLine = true, modifier = Modifier.weight(1f))
                        Spacer(Modifier.width(6.dp))
                        Text(if (matches.isEmpty()) "0/0" else "${(activeMatch.coerceAtLeast(0) + 1).coerceAtMost(matches.size)}/${matches.size}", style = MaterialTheme.typography.bodySmall)
                        Box {
                            IconButton(onClick = { showSearchMenu = true }) { Icon(painterResource(R.drawable.mt_ic_more), "Search Options") }
                            DropdownMenu(expanded = showSearchMenu, onDismissRequest = { showSearchMenu = false }) {
                                DropdownMenuItem(text = { Row(verticalAlignment = Alignment.CenterVertically) { Text("Regex"); Spacer(Modifier.weight(1f)); Checkbox(isRegex, null) } }, onClick = { isRegex = !isRegex; activeMatch = -1 })
                                DropdownMenuItem(text = { Row(verticalAlignment = Alignment.CenterVertically) { Text("Match case"); Spacer(Modifier.weight(1f)); Checkbox(isMatchCase, null) } }, onClick = { isMatchCase = !isMatchCase; activeMatch = -1 })
                            }
                        }
                    }
                    OutlinedTextField(replacement, { replacement = it }, placeholder = { Text("Replace with") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        TextButton(enabled = matches.isNotEmpty(), onClick = { selectMatch(if (activeMatch < 0) matches.lastIndex else activeMatch - 1) }) { Text("PREV") }
                        TextButton(enabled = matches.isNotEmpty(), onClick = { selectMatch(activeMatch + 1) }) { Text("NEXT") }
                        TextButton(enabled = matches.isNotEmpty(), onClick = { replaceCurrent(false) }) { Text("REP") }
                        TextButton(enabled = matches.isNotEmpty(), onClick = { replaceCurrent(true) }) { Text("ALL") }
                        TextButton(onClick = { showSearchPanel = false }) { Text("CLOSE") }
                    }
                }
            }
        }
    }
    }
}

private fun findMatches(text: String, query: String, regex: Boolean, matchCase: Boolean): List<EditorMatch> {
    if (query.isEmpty()) return emptyList()
    return if (regex) {
        val options = if (matchCase) emptySet() else setOf(RegexOption.IGNORE_CASE)
        runCatching { Regex(query, options).findAll(text).filter { it.value.isNotEmpty() }.map { EditorMatch(it.range.first, it.range.last + 1) }.toList() }.getOrDefault(emptyList())
    } else {
        buildList {
            var from = 0
            while (from <= text.length - query.length) {
                val index = text.indexOf(query, from, ignoreCase = !matchCase)
                if (index < 0) break
                add(EditorMatch(index, index + query.length))
                from = index + query.length.coerceAtLeast(1)
            }
        }
    }
}

private fun replaceAllMatches(text: String, query: String, replacement: String, regex: Boolean, matchCase: Boolean): String {
    if (query.isEmpty()) return text
    return if (regex) {
        val options = if (matchCase) emptySet() else setOf(RegexOption.IGNORE_CASE)
        runCatching { Regex(query, options).replace(text, replacement) }.getOrDefault(text)
    } else if (matchCase) text.replace(query, replacement)
    else Regex(Regex.escape(query), RegexOption.IGNORE_CASE).replace(text, replacement)
}

private fun replacementForMatch(value: String, query: String, replacement: String, regex: Boolean, matchCase: Boolean): String {
    if (!regex) return replacement
    val options = if (matchCase) emptySet() else setOf(RegexOption.IGNORE_CASE)
    return runCatching { Regex(query, options).replaceFirst(value, replacement) }.getOrDefault(replacement)
}
