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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FlipToBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SelectionBottomBar(
    selectedCount: Int,
    onSelectAll: () -> Unit,
    onInvertSelection: () -> Unit,
    onCloseSelected: () -> Unit,
    onDeleteSelected: () -> Unit,
    onArchiveSelected: () -> Unit,
    onMoreOptions: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .background(Color(0xFF212121)), // Dark MT Manager bottom bar
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        BottomBarActionItem(
            icon = Icons.Outlined.SelectAll,
            label = "Select All",
            onClick = onSelectAll
        )

        BottomBarActionItem(
            icon = Icons.Default.FlipToBack,
            label = "Invert",
            onClick = onInvertSelection
        )

        BottomBarActionItem(
            icon = Icons.Default.Close,
            label = "Close",
            onClick = onCloseSelected
        )

        BottomBarActionItem(
            icon = Icons.Default.Delete,
            label = "Delete",
            onClick = onDeleteSelected
        )

        BottomBarActionItem(
            icon = Icons.Default.Archive,
            label = "Archive",
            onClick = onArchiveSelected
        )

        BottomBarActionItem(
            icon = Icons.Default.MoreVert,
            label = "More",
            onClick = onMoreOptions
        )
    }
}

@Composable
private fun BottomBarActionItem(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = Color.White,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            fontSize = 10.sp,
            color = Color.White
        )
    }
}