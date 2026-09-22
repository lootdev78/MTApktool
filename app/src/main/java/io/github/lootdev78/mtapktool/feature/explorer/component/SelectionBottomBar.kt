package io.github.lootdev78.mtapktool.feature.explorer.component

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.lootdev78.mtapktool.R
import io.github.lootdev78.mtapktool.core.theme.MtClassicMetrics

@Composable
fun SelectionBottomBar(
    selectedCount: Int,
    onSelectAll: () -> Unit,
    onCopySelected: () -> Unit,
    onMoveSelected: () -> Unit,
    onDeleteSelected: () -> Unit,
    onMoreOptions: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().height(MtClassicMetrics.bottomBarHeight).background(MaterialTheme.colorScheme.surfaceVariant),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        BottomBarActionItem(R.drawable.mt_ic_check, "Alle", onSelectAll)
        BottomBarActionItem(R.drawable.mt_ic_copy, "Kopieren", onCopySelected)
        BottomBarActionItem(R.drawable.mt_ic_move, "Verschieben", onMoveSelected)
        BottomBarActionItem(R.drawable.mt_ic_delete, "Löschen", onDeleteSelected)
        BottomBarActionItem(R.drawable.mt_ic_more, "Mehr", onMoreOptions)
    }
}

@Composable
private fun BottomBarActionItem(@DrawableRes iconRes: Int, label: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxHeight().clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(painterResource(iconRes), contentDescription = label, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        Spacer(Modifier.height(2.dp))
        Text(label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
