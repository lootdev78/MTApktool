package io.github.lootdev78.mtapktool.feature.explorer.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Bookmark as BookmarkIcon
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import io.github.lootdev78.mtapktool.feature.explorer.state.Bookmark

/** Bottom bookmark panel opened by an upward gesture from the explorer action bar. */
@Composable
fun BookmarkBottomSheet(
    bookmarks: List<Bookmark>,
    currentPath: String,
    activePane: ActivePane,
    onDismiss: () -> Unit,
    onOpen: (ActivePane, String) -> Unit,
    onAddCurrent: () -> Unit,
    onEdit: (Bookmark) -> Unit,
    onRemove: (Bookmark) -> Unit,
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
                        Icon(Icons.Outlined.BookmarkIcon, contentDescription = null)
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
                        Text("Nach links wischen oder lange drücken für Optionen", Modifier.padding(horizontal = 16.dp, vertical = 8.dp), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        LazyColumn(Modifier.fillMaxWidth().weight(1f, fill = false)) {
                            items(bookmarks, key = { it.path }) { bookmark ->
                                BookmarkRow(
                                    bookmark = bookmark,
                                    activePane = activePane,
                                    onOpen = { pane -> onOpen(pane, bookmark.path) },
                                    onEdit = { onEdit(bookmark) },
                                    onRemove = { onRemove(bookmark) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
