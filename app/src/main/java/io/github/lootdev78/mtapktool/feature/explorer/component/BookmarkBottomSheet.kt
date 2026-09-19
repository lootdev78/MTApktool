package io.github.lootdev78.mtapktool.feature.explorer.component

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.SwapHoriz
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.lootdev78.mtapktool.core.i18n.UiText
import io.github.lootdev78.mtapktool.feature.explorer.bookmark.BookmarkState
import io.github.lootdev78.mtapktool.feature.explorer.bookmark.BookmarkStore
import io.github.lootdev78.mtapktool.feature.explorer.viewmodel.ActivePane
import java.io.File

/**
 * Pull-up bookmark bar modeled after MT's file-manager workflow, implemented entirely
 * in MTApktool. Groups and ordering are persisted by [BookmarkStore].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookmarkBottomSheet(
    state: BookmarkState,
    activePane: ActivePane,
    gesturePane: ActivePane,
    currentPath: String,
    onChange: (BookmarkState) -> Unit,
    onOpen: (ActivePane, String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    var groupId by remember { mutableStateOf(state.groups.firstOrNull()?.id ?: BookmarkState.DEFAULT) }
    val group = state.groups.firstOrNull { it.id == groupId } ?: state.groups.firstOrNull() ?: return
    var menu by remember { mutableStateOf(false) }
    var addGroup by remember { mutableStateOf(false) }
    var renameGroup by remember { mutableStateOf(false) }
    var deleteGroup by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<String?>(null) }
    var pendingMove by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = MaterialTheme.shapes.small,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 6.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(UiText.t("Bookmarks", "Lesezeichen"), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            IconButton(onClick = { onChange(BookmarkStore.add(state, listOf(currentPath), group.id)) }) {
                Icon(Icons.Default.BookmarkAdd, contentDescription = UiText.t("Add current path", "Aktuellen Pfad hinzufügen"))
            }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, contentDescription = UiText.t("Bookmark options", "Lesezeichenoptionen")) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text(UiText.t("Add group", "Gruppe hinzufügen")) },
                        onClick = { menu = false; addGroup = true },
                        leadingIcon = { Icon(Icons.Default.Add, null) },
                    )
                    DropdownMenuItem(
                        text = { Text(UiText.t("Rename group", "Gruppe umbenennen")) },
                        onClick = { menu = false; renameGroup = true },
                        leadingIcon = { Icon(Icons.Default.Edit, null) },
                        enabled = group.id != BookmarkState.DEFAULT,
                    )
                    DropdownMenuItem(
                        text = { Text(UiText.t("Delete group", "Gruppe löschen")) },
                        onClick = { menu = false; deleteGroup = true },
                        leadingIcon = { Icon(Icons.Default.Delete, null) },
                        enabled = group.id != BookmarkState.DEFAULT,
                    )
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text(UiText.t("Add new bookmarks at top", "Neue Lesezeichen oben hinzufügen")) },
                        onClick = { onChange(state.copy(addNewToTop = !state.addNewToTop)) },
                        trailingIcon = {
                            Switch(
                                checked = state.addNewToTop,
                                onCheckedChange = { onChange(state.copy(addNewToTop = it)) },
                            )
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(UiText.t("Use swipe position for target pane", "Wischposition für Zielfenster verwenden")) },
                        onClick = { onChange(state.copy(positionAwareSwipe = !state.positionAwareSwipe)) },
                        trailingIcon = {
                            Switch(
                                checked = state.positionAwareSwipe,
                                onCheckedChange = { onChange(state.copy(positionAwareSwipe = it)) },
                            )
                        },
                    )
                }
            }
        }

        if (state.groups.size > 1) {
            ScrollableTabRow(
                selectedTabIndex = state.groups.indexOfFirst { it.id == group.id }.coerceAtLeast(0),
                edgePadding = 8.dp,
                containerColor = MaterialTheme.colorScheme.surface,
                divider = { HorizontalDivider() },
            ) {
                state.groups.forEach { item ->
                    Tab(
                        selected = item.id == group.id,
                        onClick = { groupId = item.id },
                        text = { Text(item.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    )
                }
            }
        } else {
            HorizontalDivider()
        }

        if (group.paths.isEmpty()) {
            Box(Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.BookmarkAdd, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    Text(UiText.t("No bookmarks", "Keine Lesezeichen"), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = { onChange(BookmarkStore.add(state, listOf(currentPath), group.id)) }) {
                        Text(UiText.t("ADD CURRENT PATH", "AKTUELLEN PFAD HINZUFÜGEN"))
                    }
                }
            }
        } else {
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 520.dp)) {
                itemsIndexed(group.paths, key = { _, path -> "${group.id}:$path" }) { index, path ->
                    BookmarkRow(
                        path = path,
                        index = index,
                        total = group.paths.size,
                        onOpen = {
                            val pane = if (state.positionAwareSwipe) gesturePane else activePane
                            onOpen(pane, path)
                            onDismiss()
                        },
                        onOpenOther = {
                            val base = if (state.positionAwareSwipe) gesturePane else activePane
                            onOpen(if (base == ActivePane.LEFT) ActivePane.RIGHT else ActivePane.LEFT, path)
                            onDismiss()
                        },
                        onMoveUp = { onChange(BookmarkStore.move(state, group.id, path, -1)) },
                        onMoveDown = { onChange(BookmarkStore.move(state, group.id, path, 1)) },
                        onMoveGroup = { pendingMove = path },
                        onDelete = { pendingDelete = path },
                    )
                    HorizontalDivider(Modifier.padding(start = 52.dp))
                }
            }
        }

        Text(
            UiText.t("Swipe upward from the bottom file bar to open bookmarks.", "Von der unteren Dateileiste nach oben ziehen, um Lesezeichen zu öffnen."),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    if (addGroup) {
        NamePrompt(UiText.t("Add group", "Gruppe hinzufügen"), "", { name ->
            val next = BookmarkStore.addGroup(state, name)
            onChange(next)
            next.groups.lastOrNull()?.let { groupId = it.id }
            addGroup = false
        }, { addGroup = false })
    }
    if (renameGroup) {
        NamePrompt(UiText.t("Rename group", "Gruppe umbenennen"), group.name, { name ->
            onChange(BookmarkStore.renameGroup(state, group.id, name))
            renameGroup = false
        }, { renameGroup = false })
    }
    if (deleteGroup) {
        AlertDialog(
            onDismissRequest = { deleteGroup = false },
            title = { Text(UiText.t("Delete group", "Gruppe löschen")) },
            text = { Text(UiText.t("Bookmarks in this group are moved to Default.", "Lesezeichen dieser Gruppe werden in Standard verschoben.")) },
            confirmButton = {
                TextButton(onClick = {
                    onChange(BookmarkStore.deleteGroup(state, group.id))
                    groupId = BookmarkState.DEFAULT
                    deleteGroup = false
                }) { Text(UiText.t("DELETE", "LÖSCHEN")) }
            },
            dismissButton = { TextButton(onClick = { deleteGroup = false }) { Text(UiText.t("CANCEL", "ABBRECHEN")) } },
        )
    }
    pendingDelete?.let { path ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(UiText.t("Remove bookmark", "Lesezeichen entfernen")) },
            text = { Text(File(path).name.ifBlank { path }) },
            confirmButton = {
                TextButton(onClick = {
                    onChange(BookmarkStore.remove(state, path))
                    pendingDelete = null
                }) { Text(UiText.t("REMOVE", "ENTFERNEN")) }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text(UiText.t("CANCEL", "ABBRECHEN")) } },
        )
    }
    pendingMove?.let { path ->
        AlertDialog(
            onDismissRequest = { pendingMove = null },
            title = { Text(UiText.t("Move to group", "In Gruppe verschieben")) },
            text = {
                Column {
                    state.groups.filterNot { it.id == group.id }.forEach { target ->
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable {
                                onChange(BookmarkStore.add(state, listOf(path), target.id))
                                groupId = target.id
                                pendingMove = null
                            }.padding(vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Default.Folder, null, tint = MaterialTheme.colorScheme.tertiary)
                            Spacer(Modifier.width(12.dp))
                            Text(target.name)
                        }
                    }
                    if (state.groups.size <= 1) Text(UiText.t("Create another group first.", "Zuerst eine weitere Gruppe erstellen."))
                }
            },
            confirmButton = { TextButton(onClick = { pendingMove = null }) { Text(UiText.t("CLOSE", "SCHLIESSEN")) } },
        )
    }
}

@Composable
private fun BookmarkRow(
    path: String,
    index: Int,
    total: Int,
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
        Icon(Icons.Default.Folder, null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(File(path).name.ifBlank { path }, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(path, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, contentDescription = UiText.t("Bookmark options", "Lesezeichenoptionen")) }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                    text = { Text(UiText.t("Open in other pane", "Im anderen Fenster öffnen")) },
                    onClick = { menu = false; onOpenOther() },
                    leadingIcon = { Icon(Icons.Default.SwapHoriz, null) },
                )
                DropdownMenuItem(
                    text = { Text(UiText.t("Move up", "Nach oben")) },
                    onClick = { menu = false; onMoveUp() },
                    enabled = index > 0,
                    leadingIcon = { Icon(Icons.Default.KeyboardArrowUp, null) },
                )
                DropdownMenuItem(
                    text = { Text(UiText.t("Move down", "Nach unten")) },
                    onClick = { menu = false; onMoveDown() },
                    enabled = index < total - 1,
                    leadingIcon = { Icon(Icons.Default.KeyboardArrowDown, null) },
                )
                DropdownMenuItem(
                    text = { Text(UiText.t("Move to group", "In Gruppe verschieben")) },
                    onClick = { menu = false; onMoveGroup() },
                    leadingIcon = { Icon(Icons.Default.DriveFileMove, null) },
                )
                HorizontalDivider()
                DropdownMenuItem(
                    text = { Text(UiText.t("Remove", "Entfernen")) },
                    onClick = { menu = false; onDelete() },
                    leadingIcon = { Icon(Icons.Default.Delete, null) },
                )
            }
        }
    }
}

@Composable
private fun NamePrompt(
    title: String,
    initial: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var value by remember(initial) { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(value = value, onValueChange = { value = it }, singleLine = true) },
        confirmButton = { TextButton(onClick = { onSave(value) }, enabled = value.isNotBlank()) { Text(io.github.lootdev78.mtapktool.core.i18n.UiText.auto("OK")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(UiText.t("CANCEL", "ABBRECHEN")) } },
    )
}
