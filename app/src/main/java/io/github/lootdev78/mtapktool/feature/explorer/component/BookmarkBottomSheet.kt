package io.github.lootdev78.mtapktool.feature.explorer.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.lootdev78.mtapktool.core.theme.MtClassicMetrics
import io.github.lootdev78.mtapktool.feature.explorer.viewmodel.ActivePane
import java.io.File

/** Bottom bookmark panel opened by an upward gesture from the explorer action bar. */
@Composable
fun BookmarkBottomSheet(
    bookmarks: List<String>,
    currentPath: String,
    activePane: ActivePane,
    onDismiss: () -> Unit,
    onOpen: (ActivePane, String) -> Unit,
    onAddCurrent: () -> Unit,
    onRemove: (String) -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            Surface(
                modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp),
                color = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
                shadowElevation = 12.dp,
            ) {
                Column(Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().height(MtClassicMetrics.toolbarHeight).padding(start = 16.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Outlined.Bookmark, contentDescription = null)
                        Spacer(Modifier.width(10.dp))
                        Text("Lesezeichen", fontSize = MtClassicMetrics.title, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                        IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "Schließen") }
                    }
                    HorizontalDivider()
                    Row(
                        modifier = Modifier.fillMaxWidth().height(MtClassicMetrics.drawerRowHeight).clickable(onClick = onAddCurrent).padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Aktuellen Pfad hinzufügen", fontSize = MtClassicMetrics.drawerText)
                            Text(currentPath, fontSize = MtClassicMetrics.drawerMeta, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    HorizontalDivider()
                    if (bookmarks.isEmpty()) {
                        Box(Modifier.fillMaxWidth().padding(28.dp), contentAlignment = Alignment.Center) {
                            Text("Keine Lesezeichen", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else {
                        bookmarks.forEach { path ->
                            BookmarkRow(
                                path = path,
                                activePane = activePane,
                                onOpen = { pane -> onOpen(pane, path) },
                                onRemove = { onRemove(path) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BookmarkRow(
    path: String,
    activePane: ActivePane,
    onOpen: (ActivePane) -> Unit,
    onRemove: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth().height(MtClassicMetrics.drawerRowHeight).clickable { onOpen(activePane) }.padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(File(path).name.ifBlank { path }, fontSize = MtClassicMetrics.drawerText, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(path, fontSize = MtClassicMetrics.drawerMeta, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "Lesezeichenoptionen") }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("Links öffnen") }, onClick = { menu = false; onOpen(ActivePane.LEFT) })
                DropdownMenuItem(text = { Text("Rechts öffnen") }, onClick = { menu = false; onOpen(ActivePane.RIGHT) })
                DropdownMenuItem(
                    text = { Text("Entfernen") },
                    leadingIcon = { Icon(Icons.Default.DeleteOutline, null) },
                    onClick = { menu = false; onRemove() },
                )
            }
        }
    }
}
