package io.github.lootdev78.mtapktool.feature.explorer.component

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FlipToBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.lootdev78.mtapktool.core.i18n.UiText

/** MT selection toolbar: Copy, Move, Delete are direct; More holds secondary actions; Close exits selection. */
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
        modifier = modifier.fillMaxWidth().height(54.dp).background(MaterialTheme.colorScheme.surfaceVariant),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Operation(Icons.Default.ContentCopy, UiText.t("Copy", "Kopieren"), onCopySelected, Modifier.weight(1f))
        Operation(Icons.AutoMirrored.Filled.DriveFileMove, UiText.t("Move", "Verschieben"), onMoveSelected, Modifier.weight(1f))
        Operation(Icons.Default.Delete, UiText.t("Delete", "Löschen"), onDeleteSelected, Modifier.weight(1f))
        IconButton(onClick = onMoreOptions, modifier = Modifier.size(44.dp)) {
            Icon(Icons.Default.MoreVert, contentDescription = UiText.t("More", "Mehr"), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = onDone, modifier = Modifier.size(44.dp)) {
            Icon(Icons.Default.Close, contentDescription = UiText.t("Done", "Fertig"), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun SelectionMoreDialog(
    selectedCount: Int,
    onArchiveSelected: () -> Unit,
    onAddBookmark: () -> Unit,
    onSelectAll: () -> Unit,
    onInvertSelection: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(UiText.t("$selectedCount selected", "$selectedCount ausgewählt")) },
        text = {
            Column {
                ActionRow(Icons.Default.Archive, UiText.t("Compress", "Komprimieren")) { onArchiveSelected(); onDismiss() }
                ActionRow(Icons.Outlined.BookmarkAdd, UiText.t("Add to bookmarks", "Zu Lesezeichen hinzufügen")) { onAddBookmark(); onDismiss() }
                HorizontalDivider()
                ActionRow(Icons.Outlined.SelectAll, UiText.t("Select all", "Alles auswählen")) { onSelectAll(); onDismiss() }
                ActionRow(Icons.Default.FlipToBack, UiText.t("Invert selection", "Auswahl umkehren")) { onInvertSelection(); onDismiss() }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(UiText.t("CLOSE", "SCHLIESSEN")) } },
    )
}

@Composable
private fun ActionRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(16.dp))
        Text(label, fontSize = 16.sp)
    }
}

@Composable
private fun Operation(icon: ImageVector, label: String, onClick: () -> Unit, modifier: Modifier) {
    Row(
        modifier = modifier.fillMaxHeight().clickable(onClick = onClick).padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(icon, contentDescription = label, modifier = Modifier.size(21.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(5.dp))
        Text(label, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
