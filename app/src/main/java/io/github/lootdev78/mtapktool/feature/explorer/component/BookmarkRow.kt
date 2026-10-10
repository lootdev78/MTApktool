package io.github.lootdev78.mtapktool.feature.explorer.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.outlined.Bookmark as BookmarkIcon
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import io.github.lootdev78.mtapktool.core.theme.MtClassicMetrics
import io.github.lootdev78.mtapktool.feature.explorer.state.Bookmark
import io.github.lootdev78.mtapktool.feature.explorer.viewmodel.ActivePane
import kotlin.math.roundToInt

/** Swipe reveals actions; long press and accessibility actions offer the same commands. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BookmarkRow(
    bookmark: Bookmark,
    activePane: ActivePane,
    onOpen: (ActivePane) -> Unit,
    onEdit: () -> Unit,
    onRemove: () -> Unit,
    containerColor: Color = MaterialTheme.colorScheme.surface,
) {
    val actionWidth = with(LocalDensity.current) { 96.dp.toPx() }
    var menu by remember(bookmark.path) { mutableStateOf(false) }
    var revealed by remember(bookmark.path) { mutableStateOf(false) }
    var dragging by remember(bookmark.path) { mutableStateOf(false) }
    var offset by remember(bookmark.path) { mutableFloatStateOf(0f) }
    val position by animateFloatAsState(
        if (dragging) offset else if (revealed) -actionWidth else 0f,
        animationSpec = if (dragging) snap() else tween(140),
        label = "bookmark_actions",
    )
    val open = rememberUpdatedState(onOpen)
    val edit = rememberUpdatedState(onEdit)
    val remove = rememberUpdatedState(onRemove)
    Box(Modifier.fillMaxWidth().height(MtClassicMetrics.drawerRowHeight)) {
        if (revealed || dragging) {
            Row(Modifier.align(Alignment.CenterEnd).width(96.dp).fillMaxHeight(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { revealed = false; edit.value() }) { Icon(Icons.Default.Edit, "Lesezeichen bearbeiten", tint = MaterialTheme.colorScheme.primary) }
                IconButton(onClick = { revealed = false; remove.value() }) { Icon(Icons.Default.DeleteOutline, "Lesezeichen löschen", tint = MaterialTheme.colorScheme.error) }
            }
        }
        Surface(
            color = containerColor,
            contentColor = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.fillMaxSize().offset { IntOffset(position.roundToInt(), 0) }
                .pointerInput(bookmark.path, actionWidth) {
                    detectHorizontalDragGestures(
                        onDragStart = { offset = if (revealed) -actionWidth else 0f; dragging = true },
                        onHorizontalDrag = { change, amount -> change.consume(); offset = (offset + amount).coerceIn(-actionWidth, 0f) },
                        onDragEnd = { revealed = offset < -actionWidth / 3f; dragging = false },
                        onDragCancel = { dragging = false },
                    )
                }
                .combinedClickable(
                    onClick = { if (revealed) revealed = false else open.value(activePane) },
                    onLongClick = { menu = true },
                )
                .semantics {
                    customActions = listOf(
                        CustomAccessibilityAction("Lesezeichen bearbeiten") { edit.value(); true },
                        CustomAccessibilityAction("Lesezeichen löschen") { remove.value(); true },
                    )
                },
        ) {
            Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.BookmarkIcon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(bookmark.name, fontSize = MtClassicMetrics.drawerText, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(bookmark.path, fontSize = MtClassicMetrics.drawerMeta, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(text = { Text("Links öffnen") }, onClick = { menu = false; revealed = false; open.value(ActivePane.LEFT) })
            DropdownMenuItem(text = { Text("Rechts öffnen") }, onClick = { menu = false; revealed = false; open.value(ActivePane.RIGHT) })
            DropdownMenuItem(text = { Text("Bearbeiten") }, leadingIcon = { Icon(Icons.Default.Edit, null) }, onClick = { menu = false; revealed = false; edit.value() })
            DropdownMenuItem(text = { Text("Löschen") }, leadingIcon = { Icon(Icons.Default.DeleteOutline, null) }, onClick = { menu = false; revealed = false; remove.value() })
        }
    }
}
