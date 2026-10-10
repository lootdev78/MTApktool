package io.github.lootdev78.mtapktool.feature.explorer.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.annotation.DrawableRes
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.graphicsLayer
import io.github.lootdev78.mtapktool.R
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.lootdev78.mtapktool.core.theme.MtClassicMetrics

/** MP-style selection controls; transfers use the focused source pane. */
@Composable
fun SelectionBottomBar(
    selectedCount: Int,
    onSelectAll: () -> Unit,
    onCopySelected: () -> Unit,
    onMoveSelected: () -> Unit,
    onDeleteSelected: () -> Unit,
    onMoreOptions: () -> Unit,
    onInvertSelection: () -> Unit,
    onSelectSameType: () -> Unit,
    onExitSelection: () -> Unit,
    destinationLabel: String,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    Surface(modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurfaceVariant) {
        Column {
            Row(Modifier.fillMaxWidth().height(38.dp).padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("$selectedCount → $destinationLabel", Modifier.weight(1f), fontSize = 11.sp, maxLines = 1)
                IconButton(onClick = onCopySelected, enabled = enabled, modifier = Modifier.size(38.dp)) { Icon(Icons.Default.ContentCopy, "In anderes Panel kopieren", Modifier.size(20.dp)) }
                IconButton(onClick = onMoveSelected, enabled = enabled, modifier = Modifier.size(38.dp)) { Icon(Icons.Default.DriveFileMove, "In anderes Panel verschieben", Modifier.size(20.dp)) }
                IconButton(onClick = onDeleteSelected, enabled = enabled, modifier = Modifier.size(38.dp)) { Icon(Icons.Default.Delete, "Auswahl löschen", Modifier.size(20.dp)) }
            }
            HorizontalDivider()
            Row(Modifier.fillMaxWidth().height(MtClassicMetrics.bottomBarHeight), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceEvenly) {
                SelectionAction(R.drawable.mt_ic_select_all, "Alle", enabled, onSelectAll)
                SelectionAction(R.drawable.mt_ic_invert_selection, "Umkehren", enabled, onInvertSelection)
                SelectionAction(R.drawable.mt_ic_add, "Beenden", enabled, onExitSelection)
                SelectionAction(R.drawable.mt_ic_select_type, "Gleicher Typ", enabled, onSelectSameType)
                SelectionAction(R.drawable.mt_ic_selection_info, "Mehr", enabled, onMoreOptions)
            }
        }
    }
}

@Composable
private fun RowScope.SelectionAction(@DrawableRes icon: Int, label: String, enabled: Boolean, click: () -> Unit) {
    Column(Modifier.weight(1f).fillMaxHeight().clickable(enabled = enabled, onClick = click).padding(vertical = 5.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(painterResource(icon), label, Modifier.size(24.dp).graphicsLayer { rotationZ = if (icon == R.drawable.mt_ic_add) 45f else 0f }, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(label, fontSize = 9.sp, maxLines = 1, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 1f else 0.38f))
    }
}
