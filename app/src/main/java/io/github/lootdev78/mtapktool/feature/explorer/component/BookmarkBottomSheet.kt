package io.github.lootdev78.mtapktool.feature.explorer.component

import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.lootdev78.mtapktool.R
import io.github.lootdev78.mtapktool.feature.explorer.bookmark.BookmarkGroup
import io.github.lootdev78.mtapktool.feature.explorer.bookmark.BookmarkState
import io.github.lootdev78.mtapktool.feature.explorer.bookmark.BookmarkStore
import io.github.lootdev78.mtapktool.feature.explorer.viewmodel.ActivePane
import java.io.File

/**
 * MT-style bookmark drawer opened from the file-manager bottom toolbar.
 * It deliberately stays inside the existing explorer feature set: grouped paths,
 * ordering and pane-aware navigation, without introducing a separate manager screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookmarkBottomSheet(
    state: BookmarkState,
    activePane: ActivePane,
    gesturePane: ActivePane,
    currentPath: String,
    onStateChange: (BookmarkState) -> Unit,
    onOpenPath: (ActivePane, String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    var selectedGroupId by remember {
        mutableStateOf(state.groups.firstOrNull()?.id ?: BookmarkState.DEFAULT_GROUP_ID)
    }
    val selectedGroup = state.groups.firstOrNull { it.id == selectedGroupId } ?: state.defaultGroup
    var headerMenu by remember { mutableStateOf(false) }
    var addGroupDialog by remember { mutableStateOf(false) }
    var renameGroupDialog by remember { mutableStateOf(false) }
    var deleteGroupDialog by remember { mutableStateOf(false) }
    var pendingDeletePath by remember { mutableStateOf<String?>(null) }
    var pendingMovePath by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp),
        dragHandle = {
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = 7.dp, bottom = 3.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Surface(
                    modifier = Modifier.width(42.dp).height(4.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
                    shape = RoundedCornerShape(2.dp),
                ) {}
            }
        },
    ) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 6.dp, top = 2.dp, bottom = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Lesezeichen",
                    modifier = Modifier.weight(1f),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Medium,
                )
                IconButton(
                    onClick = {
                        onStateChange(BookmarkStore.addPath(state, currentPath, selectedGroup.id))
                    },
                ) {
                    Icon(painterResource(R.drawable.mt_ic_bookmark_add), contentDescription = "Aktuellen Pfad hinzufügen")
                }
                Box {
                    IconButton(onClick = { headerMenu = true }) {
                        Icon(painterResource(R.drawable.mt_ic_more), contentDescription = "Lesezeichenoptionen")
                    }
                    DropdownMenu(expanded = headerMenu, onDismissRequest = { headerMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("Gruppe hinzufügen") },
                            leadingIcon = { Icon(painterResource(R.drawable.mt_ic_add), null) },
                            onClick = { headerMenu = false; addGroupDialog = true },
                        )
                        DropdownMenuItem(
                            text = { Text("Gruppe umbenennen") },
                            leadingIcon = { Icon(painterResource(R.drawable.mt_ic_rename), null) },
                            enabled = selectedGroup.id != BookmarkState.DEFAULT_GROUP_ID,
                            onClick = { headerMenu = false; renameGroupDialog = true },
                        )
                        DropdownMenuItem(
                            text = { Text("Gruppe löschen") },
                            leadingIcon = { Icon(painterResource(R.drawable.mt_ic_delete), null) },
                            enabled = selectedGroup.id != BookmarkState.DEFAULT_GROUP_ID,
                            onClick = { headerMenu = false; deleteGroupDialog = true },
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text("Neue Lesezeichen oben hinzufügen")
                                    Text(
                                        if (state.addNewToTop) "Neue Einträge stehen am Anfang der Gruppe." else "Neue Einträge stehen am Ende der Gruppe.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            },
                            trailingIcon = {
                                Switch(
                                    checked = state.addNewToTop,
                                    onCheckedChange = { onStateChange(state.copy(addNewToTop = it)) },
                                )
                            },
                            onClick = { onStateChange(state.copy(addNewToTop = !state.addNewToTop)) },
                        )
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text("Fensterposition beim Hochziehen beachten")
                                    Text(
                                        "Links oder rechts geöffnetes Lesezeichen folgt der Fingerposition.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            },
                            trailingIcon = {
                                Switch(
                                    checked = state.positionAwareSwipe,
                                    onCheckedChange = { onStateChange(state.copy(positionAwareSwipe = it)) },
                                )
                            },
                            onClick = { onStateChange(state.copy(positionAwareSwipe = !state.positionAwareSwipe)) },
                        )
                    }
                }
            }

            if (state.groups.size > 1) {
                val selectedIndex = state.groups.indexOfFirst { it.id == selectedGroup.id }.coerceAtLeast(0)
                ScrollableTabRow(
                    selectedTabIndex = selectedIndex,
                    edgePadding = 8.dp,
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.primary,
                    divider = { HorizontalDivider() },
                ) {
                    state.groups.forEach { group ->
                        Tab(
                            selected = group.id == selectedGroup.id,
                            onClick = { selectedGroupId = group.id },
                            text = { Text(group.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        )
                    }
                }
            } else {
                HorizontalDivider()
            }

            if (selectedGroup.paths.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 180.dp).padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        painterResource(R.drawable.mt_ic_bookmark_add),
                        contentDescription = null,
                        modifier = Modifier.size(36.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(10.dp))
                    Text("Keine Lesezeichen in dieser Gruppe", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = { onStateChange(BookmarkStore.addPath(state, currentPath, selectedGroup.id)) }) {
                        Text("AKTUELLEN PFAD HINZUFÜGEN")
                    }
                }
            } else {
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 520.dp)) {
                    itemsIndexed(selectedGroup.paths, key = { _, path -> "${selectedGroup.id}:$path" }) { index, path ->
                        BookmarkRow(
                            path = path,
                            index = index,
                            count = selectedGroup.paths.size,
                            onOpen = {
                                val pane = if (state.positionAwareSwipe) gesturePane else activePane
                                onOpenPath(pane, path)
                                onDismiss()
                            },
                            onOpenOther = {
                                val base = if (state.positionAwareSwipe) gesturePane else activePane
                                val pane = if (base == ActivePane.LEFT) ActivePane.RIGHT else ActivePane.LEFT
                                onOpenPath(pane, path)
                                onDismiss()
                            },
                            onMoveUp = { onStateChange(BookmarkStore.movePath(state, selectedGroup.id, path, -1)) },
                            onMoveDown = { onStateChange(BookmarkStore.movePath(state, selectedGroup.id, path, 1)) },
                            onMoveGroup = { pendingMovePath = path },
                            onDelete = { pendingDeletePath = path },
                        )
                        HorizontalDivider(Modifier.padding(start = 52.dp))
                    }
                }
            }

            Text(
                text = "Von der unteren Dateileiste nach oben ziehen, um Lesezeichen zu öffnen.",
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (addGroupDialog) {
        NameDialog(
            title = "Gruppe hinzufügen",
            initialValue = "",
            confirmLabel = "HINZUFÜGEN",
            onConfirm = { name ->
                val next = BookmarkStore.addGroup(state, name)
                onStateChange(next)
                next.groups.lastOrNull()?.takeIf { it.name.equals(name.trim(), ignoreCase = true) }?.let { selectedGroupId = it.id }
                addGroupDialog = false
            },
            onDismiss = { addGroupDialog = false },
        )
    }

    if (renameGroupDialog) {
        NameDialog(
            title = "Gruppe umbenennen",
            initialValue = selectedGroup.name,
            confirmLabel = "UMBENENNEN",
            onConfirm = { name ->
                onStateChange(BookmarkStore.renameGroup(state, selectedGroup.id, name))
                renameGroupDialog = false
            },
            onDismiss = { renameGroupDialog = false },
        )
    }

    if (deleteGroupDialog) {
        AlertDialog(
            onDismissRequest = { deleteGroupDialog = false },
            title = { Text("Gruppe löschen") },
            text = { Text("Gruppe „${selectedGroup.name}“ löschen? Die enthaltenen Lesezeichen werden in die Standardgruppe verschoben.") },
            dismissButton = { TextButton(onClick = { deleteGroupDialog = false }) { Text("ABBRECHEN") } },
            confirmButton = {
                TextButton(onClick = {
                    onStateChange(BookmarkStore.deleteGroup(state, selectedGroup.id))
                    selectedGroupId = BookmarkState.DEFAULT_GROUP_ID
                    deleteGroupDialog = false
                }) { Text("LÖSCHEN") }
            },
        )
    }

    pendingDeletePath?.let { path ->
        AlertDialog(
            onDismissRequest = { pendingDeletePath = null },
            title = { Text("Lesezeichen löschen") },
            text = { Text("Lesezeichen „${bookmarkName(path)}“ entfernen?") },
            dismissButton = { TextButton(onClick = { pendingDeletePath = null }) { Text("ABBRECHEN") } },
            confirmButton = {
                TextButton(onClick = {
                    onStateChange(BookmarkStore.removePath(state, path))
                    pendingDeletePath = null
                }) { Text("LÖSCHEN") }
            },
        )
    }

    pendingMovePath?.let { path ->
        AlertDialog(
            onDismissRequest = { pendingMovePath = null },
            title = { Text("Zu Gruppe verschieben") },
            text = {
                Column {
                    state.groups.filterNot { it.id == selectedGroup.id }.forEach { group ->
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                onStateChange(BookmarkStore.movePathToGroup(state, path, group.id))
                                pendingMovePath = null
                                selectedGroupId = group.id
                            }.padding(vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(painterResource(R.drawable.mt_ic_folder), null, modifier = Modifier.size(22.dp))
                            Spacer(Modifier.width(12.dp))
                            Text(group.name)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { pendingMovePath = null }) { Text("SCHLIESSEN") } },
        )
    }
}

@Composable
private fun BookmarkRow(
    path: String,
    index: Int,
    count: Int,
    onOpen: () -> Unit,
    onOpenOther: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onMoveGroup: () -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painterResource(R.drawable.mt_ic_folder),
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.tertiary,
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(bookmarkName(path), maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 15.sp)
            Text(path, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Box {
            IconButton(onClick = { menu = true }) {
                Icon(painterResource(R.drawable.mt_ic_more), contentDescription = "Lesezeichenoptionen")
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                    text = { Text("Im anderen Fenster öffnen") },
                    leadingIcon = { Icon(painterResource(R.drawable.mt_ic_swap), null) },
                    onClick = { menu = false; onOpenOther() },
                )
                DropdownMenuItem(
                    text = { Text("Nach oben") },
                    leadingIcon = { Icon(Icons.Default.ArrowUpward, null) },
                    enabled = index > 0,
                    onClick = { menu = false; onMoveUp() },
                )
                DropdownMenuItem(
                    text = { Text("Nach unten") },
                    leadingIcon = { Icon(Icons.Default.ArrowDownward, null) },
                    enabled = index < count - 1,
                    onClick = { menu = false; onMoveDown() },
                )
                DropdownMenuItem(
                    text = { Text("In Gruppe verschieben") },
                    leadingIcon = { Icon(Icons.Default.DriveFileMove, null) },
                    onClick = { menu = false; onMoveGroup() },
                )
                HorizontalDivider()
                DropdownMenuItem(
                    text = { Text("Löschen") },
                    leadingIcon = { Icon(painterResource(R.drawable.mt_ic_delete), null) },
                    onClick = { menu = false; onDelete() },
                )
            }
        }
    }
}

@Composable
fun BookmarkAddDialog(
    state: BookmarkState,
    itemCount: Int,
    onAddToGroup: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var selectedGroupId by remember(state.groups) { mutableStateOf(state.defaultGroup.id) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (itemCount == 1) "Lesezeichen hinzufügen" else "$itemCount Dateien zu Lesezeichen hinzufügen") },
        text = {
            Column {
                Text("Hinzufügen zu", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                state.groups.forEach { group ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { selectedGroupId = group.id }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            painterResource(if (group.id == selectedGroupId) R.drawable.mt_ic_bookmark_add else R.drawable.mt_ic_folder),
                            contentDescription = null,
                            modifier = Modifier.size(22.dp),
                            tint = if (group.id == selectedGroupId) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(group.name, modifier = Modifier.weight(1f))
                        Text("${group.paths.size}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    }
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("ABBRECHEN") } },
        confirmButton = { TextButton(onClick = { onAddToGroup(selectedGroupId) }) { Text("HINZUFÜGEN") } },
    )
}

@Composable
private fun NameDialog(
    title: String,
    initialValue: String,
    confirmLabel: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var value by remember(initialValue) { mutableStateOf(initialValue) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                singleLine = true,
                label = { Text("Name") },
            )
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("ABBRECHEN") } },
        confirmButton = { TextButton(enabled = value.isNotBlank(), onClick = { onConfirm(value) }) { Text(confirmLabel) } },
    )
}

private fun bookmarkName(path: String): String {
    if (path.startsWith("content://")) {
        val segment = runCatching { Uri.parse(path).lastPathSegment }.getOrNull().orEmpty()
        return segment.substringAfterLast(':').substringAfterLast('/').ifBlank { "Dokument" }
    }
    return File(path).name.ifBlank { path }
}
