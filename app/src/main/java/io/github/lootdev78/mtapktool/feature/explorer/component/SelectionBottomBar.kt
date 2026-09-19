package io.github.lootdev78.mtapktool.feature.explorer.component

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FlipToBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.lootdev78.mtapktool.R

/**
 * Selection toolbar following MT Manager's file-window action layout: the three
 * direct file operations are Copy, Move and Delete. Less frequent operations
 * live behind More, and Done only leaves selection mode.
 */
@Composable
fun SelectionBottomBar(
    onCopySelected: () -> Unit,
    onMoveSelected: () -> Unit,
    onDeleteSelected: () -> Unit,
    onMoreOptions: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(54.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SelectionBarOperation(
            iconRes = R.drawable.mt_ic_copy,
            label = "Kopieren",
            onClick = onCopySelected,
            modifier = Modifier.weight(1f),
        )
        SelectionBarOperation(
            iconRes = R.drawable.mt_ic_move,
            label = "Verschieben",
            onClick = onMoveSelected,
            modifier = Modifier.weight(1f),
        )
        SelectionBarOperation(
            iconRes = R.drawable.mt_ic_delete,
            label = "Löschen",
            onClick = onDeleteSelected,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onMoreOptions, modifier = Modifier.size(44.dp)) {
            Icon(
                painter = painterResource(R.drawable.mt_ic_more),
                contentDescription = "Mehr",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp),
            )
        }
        IconButton(onClick = onDone, modifier = Modifier.size(44.dp)) {
            Icon(
                painter = painterResource(R.drawable.mt_ic_close),
                contentDescription = "Auswahl beenden",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

@Composable
fun SelectionMoreDialog(
    selectedCount: Int,
    onArchiveSelected: () -> Unit,
    onAddBookmarks: () -> Unit,
    onSelectAll: () -> Unit,
    onInvertSelection: () -> Unit,
    onCancelSelection: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.86f),
            color = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            shape = RoundedCornerShape(2.dp),
            shadowElevation = 10.dp,
        ) {
            Column(Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 8.dp)) {
                Text(
                    text = "Ausgewählt: $selectedCount",
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.height(6.dp))
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    SelectionActionRow(R.drawable.mt_ic_compress, "Komprimieren") {
                        onArchiveSelected()
                        onDismiss()
                    }
                    SelectionActionRow(R.drawable.mt_ic_bookmark_add, "Zu Lesezeichen hinzufügen") {
                        onAddBookmarks()
                        onDismiss()
                    }
                    SelectionActionRow(R.drawable.mt_ic_select_all, "Alles auswählen") {
                        onSelectAll()
                        onDismiss()
                    }
                    SelectionVectorActionRow("Auswahl umkehren") {
                        onInvertSelection()
                        onDismiss()
                    }
                    HorizontalDivider(Modifier.padding(vertical = 4.dp))
                    SelectionActionRow(R.drawable.mt_ic_close, "Auswahl beenden") {
                        onCancelSelection()
                        onDismiss()
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("SCHLIESSEN") }
                }
            }
        }
    }
}

@Composable
private fun SelectionActionRow(
    @DrawableRes iconRes: Int,
    label: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.width(18.dp))
        Text(label, fontSize = 17.sp)
    }
}

@Composable
private fun SelectionVectorActionRow(
    label: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Default.FlipToBack,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.width(18.dp))
        Text(label, fontSize = 17.sp)
    }
}

@Composable
private fun SelectionBarOperation(
    @DrawableRes iconRes: Int,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxHeight()
            .clickable(onClick = onClick)
            .padding(horizontal = 7.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = label,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp),
        )
        Spacer(modifier = Modifier.width(5.dp))
        Text(
            text = label,
            fontSize = 14.sp,
            maxLines = 1,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
