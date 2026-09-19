package io.github.lootdev78.mtapktool.feature.explorer.component

import android.content.Context
import android.provider.DocumentsContract
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Brightness6
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.lootdev78.mtapktool.feature.explorer.model.StorageInfo
import io.github.lootdev78.mtapktool.feature.explorer.model.formatSize
import io.github.lootdev78.mtapktool.feature.explorer.model.getStorageRoots
import io.github.lootdev78.mtapktool.feature.explorer.saf.CustomLocation
import io.github.lootdev78.mtapktool.feature.explorer.viewmodel.ExplorerViewModel
import io.github.lootdev78.mtapktool.settings.ExplorerPreferences
import java.io.File
import kotlin.math.abs

@Composable
fun SideBar(
    drawerWidth: Dp,
    viewModel: ExplorerViewModel = viewModel(),
    bookmarks: List<String> = emptyList(),
    customLocations: List<CustomLocation> = emptyList(),
    onBookmarkClick: (String) -> Unit = {},
    onRemoveBookmark: (String) -> Unit = {},
    onCustomLocationClick: (CustomLocation) -> Unit = {},
    onCustomLocationLongClick: (CustomLocation) -> Unit = {},
    onCustomLocationDelete: (CustomLocation) -> Unit = {},
    onCustomLocationHide: (CustomLocation, Boolean) -> Unit = { _, _ -> },
    onCustomLocationMove: (CustomLocation, Int) -> Unit = { _, _ -> },
    onAddLocation: () -> Unit = {},
    onOpenApkExtractor: () -> Unit = {},
    onOpenTextEditor: () -> Unit = {},
    onOpenRecycleBin: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val activePane by viewModel.activePane.collectAsState()
    ExplorerPreferences.init(context)
    val explorerPrefs by ExplorerPreferences.state.collectAsState()
    var sortMode by rememberSaveable { mutableStateOf(false) }
    var headerMenu by remember { mutableStateOf(false) }
    var showHiddenLocations by rememberSaveable { mutableStateOf(false) }

    val storageRoots by produceState<List<StorageInfo>>(initialValue = emptyList(), key1 = context) {
        value = getStorageRoots(context)
    }

    val toolIds = remember(explorerPrefs.recycleBinEnabled) {
        buildList {
            add("installed")
            add("text")
            if (explorerPrefs.recycleBinEnabled) add("recycle")
        }
    }
    var toolOrder by remember(toolIds) { mutableStateOf(loadToolOrder(context, toolIds)) }

    ModalDrawerSheet(
        modifier = Modifier.fillMaxHeight().width(drawerWidth),
        drawerContainerColor = MaterialTheme.colorScheme.surface,
        drawerContentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(start = 16.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(38.dp).clip(CircleShape).background(MaterialTheme.colorScheme.onSurface),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.Android, contentDescription = null, tint = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.size(27.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(io.github.lootdev78.mtapktool.core.i18n.UiText.auto("MTApktool"), style = MaterialTheme.typography.titleLarge)
                Text(io.github.lootdev78.mtapktool.core.i18n.UiText.auto("APKTOOL"), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (sortMode) {
                IconButton(onClick = { sortMode = false }) { Icon(Icons.Default.Check, contentDescription = "Sortierung beenden") }
            } else {
                IconButton(onClick = onOpenSettings) { Icon(Icons.Default.Brightness6, contentDescription = "Theme") }
                Box {
                    IconButton(onClick = { headerMenu = true }) { Icon(Icons.Default.MoreVert, contentDescription = "Menü") }
                    DropdownMenu(expanded = headerMenu, onDismissRequest = { headerMenu = false }) {
                        DropdownMenuItem(
                            text = { Text(io.github.lootdev78.mtapktool.core.i18n.UiText.auto("Lokalen Speicher hinzufügen")) },
                            leadingIcon = { Icon(Icons.Default.Add, null) },
                            onClick = { headerMenu = false; onAddLocation() },
                        )
                        DropdownMenuItem(
                            text = { Text(io.github.lootdev78.mtapktool.core.i18n.UiText.auto("Speicher/Tools sortieren")) },
                            leadingIcon = { Icon(Icons.Default.Sort, null) },
                            onClick = { headerMenu = false; sortMode = true },
                        )
                        if (customLocations.any { it.hidden }) {
                            DropdownMenuItem(
                                text = { Text(if (showHiddenLocations) "Verborgene Speicher ausblenden" else "Verborgene Speicher anzeigen") },
                                leadingIcon = { Icon(if (showHiddenLocations) Icons.Default.VisibilityOff else Icons.Default.Visibility, null) },
                                onClick = { headerMenu = false; showHiddenLocations = !showHiddenLocations },
                            )
                        }
                        DropdownMenuItem(
                            text = { Text(io.github.lootdev78.mtapktool.core.i18n.UiText.auto("Preferences")) },
                            leadingIcon = { Icon(Icons.Default.Settings, null) },
                            onClick = { headerMenu = false; onOpenSettings() },
                        )
                    }
                }
            }
        }

        Text(
            text = "Local",
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 7.dp),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        StorageList(storages = storageRoots) { storage ->
            onClose()
            viewModel.navigateToDirectPath(activePane, storage.path)
        }

        customLocations.filter { showHiddenLocations || !it.hidden }.forEach { location ->
            CustomLocationItem(
                location = location,
                sortMode = sortMode,
                onClick = {
                    if (location.hidden) onCustomLocationHide(location, false)
                    else {
                        onClose()
                        onCustomLocationClick(location)
                    }
                },
                onRename = { onCustomLocationLongClick(location) },
                onDelete = { onCustomLocationDelete(location) },
                onHide = { onCustomLocationHide(location, !location.hidden) },
                onSort = { sortMode = true },
                onMove = { delta -> onCustomLocationMove(location, delta) },
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth().clickable { onAddLocation() }.padding(horizontal = 18.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(12.dp))
            Text(io.github.lootdev78.mtapktool.core.i18n.UiText.auto("Speicher hinzufügen"))
        }

        if (bookmarks.isNotEmpty()) {
            HorizontalDivider()
            Text(io.github.lootdev78.mtapktool.core.i18n.UiText.auto("Lesezeichen"), modifier = Modifier.padding(horizontal = 16.dp, vertical = 7.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            bookmarks.forEach { path ->
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { onClose(); onBookmarkClick(path) }.padding(start = 18.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(File(path).name.ifBlank { path }, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    IconButton(onClick = { onRemoveBookmark(path) }) { Icon(Icons.Default.DeleteOutline, contentDescription = "Lesezeichen entfernen") }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(io.github.lootdev78.mtapktool.core.i18n.UiText.auto("Network"), modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Icon(Icons.Default.ExpandMore, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(io.github.lootdev78.mtapktool.core.i18n.UiText.auto("Tools"), modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Icon(Icons.Default.ExpandLess, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        toolOrder.forEach { id ->
            when (id) {
                "installed" -> DrawerToolRow("Installed Apps", Icons.Default.Android, sortMode, onClick = { onClose(); onOpenApkExtractor() }) { delta ->
                    toolOrder = moveId(toolOrder, id, delta); saveToolOrder(context, toolOrder)
                }
                "text" -> DrawerToolRow("Text Editor", Icons.Default.EditNote, sortMode, onClick = { onClose(); onOpenTextEditor() }) { delta ->
                    toolOrder = moveId(toolOrder, id, delta); saveToolOrder(context, toolOrder)
                }
                "recycle" -> if (explorerPrefs.recycleBinEnabled) DrawerToolRow("Recycle Bin", Icons.Default.DeleteOutline, sortMode, onClick = { onClose(); onOpenRecycleBin() }) { delta ->
                    toolOrder = moveId(toolOrder, id, delta); saveToolOrder(context, toolOrder)
                }
            }
        }
    }
}

@Composable
fun StorageList(storages: List<StorageInfo>, onStorageClick: (StorageInfo) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        storages.forEach { storage -> StorageItem(storage, onStorageClick = { onStorageClick(storage) }) }
    }
}

@Composable
fun StorageItem(storage: StorageInfo, onStorageClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onStorageClick).padding(horizontal = 18.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(34.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Icon(storage.icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(storage.name, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(2.dp))
            LinearProgressIndicator(progress = { storage.usedPercentage }, modifier = Modifier.fillMaxWidth().height(2.dp))
            Spacer(Modifier.height(3.dp))
            Text(
                "${formatSize(storage.usedSpace)} used, ${formatSize(storage.freeSpace)} available",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CustomLocationItem(
    location: CustomLocation,
    sortMode: Boolean,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onHide: () -> Unit,
    onSort: () -> Unit,
    onMove: (Int) -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = { menu = true }).padding(start = 18.dp, end = 7.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(34.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(location.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 15.sp)
            Text(
                safDisplayPath(location),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (sortMode) {
            DragSortHandle(onMove)
        }
        Box {
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text(io.github.lootdev78.mtapktool.core.i18n.UiText.auto("Rename")) }, leadingIcon = { Icon(Icons.Default.Edit, null) }, onClick = { menu = false; onRename() })
                DropdownMenuItem(text = { Text(io.github.lootdev78.mtapktool.core.i18n.UiText.auto("Delete")) }, leadingIcon = { Icon(Icons.Default.Delete, null) }, onClick = { menu = false; onDelete() })
                DropdownMenuItem(
                    text = { Text(if (location.hidden) "Show" else "Hide") },
                    leadingIcon = { Icon(if (location.hidden) Icons.Default.Visibility else Icons.Default.VisibilityOff, null) },
                    onClick = { menu = false; onHide() },
                )
                DropdownMenuItem(text = { Text(io.github.lootdev78.mtapktool.core.i18n.UiText.auto("Sort")) }, leadingIcon = { Icon(Icons.Default.Sort, null) }, onClick = { menu = false; onSort() })
            }
        }
    }
}

@Composable
private fun DrawerToolRow(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    sortMode: Boolean,
    onClick: () -> Unit,
    onMove: (Int) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(enabled = !sortMode, onClick = onClick).padding(start = 18.dp, end = 7.dp, top = 7.dp, bottom = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(34.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp)) }
        Spacer(Modifier.width(12.dp))
        Text(title, modifier = Modifier.weight(1f), fontSize = 15.sp)
        if (sortMode) DragSortHandle(onMove)
    }
}

@Composable
private fun DragSortHandle(onMove: (Int) -> Unit) {
    var drag by remember { mutableFloatStateOf(0f) }
    Icon(
        Icons.Default.DragHandle,
        contentDescription = "Sortieren",
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(32.dp).pointerInput(Unit) {
            detectVerticalDragGestures(
                onDragEnd = { drag = 0f },
                onDragCancel = { drag = 0f },
            ) { change, amount ->
                change.consume()
                drag += amount
                if (abs(drag) > 42f) {
                    onMove(if (drag > 0f) 1 else -1)
                    drag = 0f
                }
            }
        },
    )
}

private fun safDisplayPath(location: CustomLocation): String = runCatching {
    val id = DocumentsContract.getTreeDocumentId(location.uri)
    val volume = id.substringBefore(':')
    val rest = id.substringAfter(':', "")
    val root = if (volume.equals("primary", true)) "/storage/emulated/0" else "/storage/$volume"
    if (rest.isBlank()) root else "$root/$rest"
}.getOrDefault("Document tree · read/write")

private const val DRAWER_PREFS = "mtapktool_drawer"
private const val TOOL_ORDER_KEY = "tool_order"

private fun loadToolOrder(context: Context, available: List<String>): List<String> {
    val raw = context.getSharedPreferences(DRAWER_PREFS, Context.MODE_PRIVATE).getString(TOOL_ORDER_KEY, "").orEmpty()
    val stored = raw.split(',').filter { it in available }
    return (stored + available.filterNot { it in stored }).distinct()
}

private fun saveToolOrder(context: Context, order: List<String>) {
    context.getSharedPreferences(DRAWER_PREFS, Context.MODE_PRIVATE).edit().putString(TOOL_ORDER_KEY, order.joinToString(",")).apply()
}

private fun moveId(order: List<String>, id: String, delta: Int): List<String> {
    val list = order.toMutableList()
    val from = list.indexOf(id)
    if (from < 0) return order
    val to = (from + delta).coerceIn(0, list.lastIndex)
    if (to == from) return order
    list.removeAt(from)
    list.add(to, id)
    return list
}
