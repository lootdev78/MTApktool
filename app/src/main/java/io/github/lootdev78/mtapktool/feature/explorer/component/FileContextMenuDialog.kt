package io.github.lootdev78.mtapktool.feature.explorer.component

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.lootdev78.mtapktool.R
import io.github.lootdev78.mtapktool.feature.explorer.model.FileItem
import io.github.lootdev78.mtapktool.feature.explorer.viewmodel.ActivePane
import io.github.lootdev78.mtapktool.settings.ExplorerPreferences
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

/**
 * File action dialog using the same action grouping and icon geometry as the MT
 * file manager. All operations are implemented by MTApktool callbacks.
 */
@Composable
fun FileContextMenuDialog(
    targetItem: FileItem?,
    activePane: ActivePane,
    onDismissRequest: () -> Unit,
    onCopy: () -> Unit,
    onMove: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onTools: () -> Unit,
    onCompress: () -> Unit,
    onProperty: () -> Unit,
    onShare: () -> Unit,
    onOpenWith: () -> Unit,
    onAddBookmark: () -> Unit,
) {
    var isVisible by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    ExplorerPreferences.init(context)
    val prefs by ExplorerPreferences.state.collectAsState()

    val addArrow: (String) -> String = { text ->
        if (activePane == ActivePane.LEFT) "$text ->" else "<- $text"
    }
    val animateDismiss = {
        scope.launch {
            isVisible = false
            delay(150.milliseconds)
            onDismissRequest()
        }
    }

    LaunchedEffect(Unit) { isVisible = true }

    Dialog(
        onDismissRequest = { animateDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        AnimatedVisibility(
            visible = isVisible,
            enter = scaleIn(
                initialScale = 0.88f,
                transformOrigin = TransformOrigin(0.5f, 0.5f),
                animationSpec = tween(160),
            ) + fadeIn(tween(160)),
            exit = scaleOut(
                targetScale = 0.9f,
                transformOrigin = TransformOrigin(0.5f, 0.5f),
                animationSpec = tween(130),
            ) + fadeOut(tween(130)),
        ) {
            Surface(
                shape = RoundedCornerShape(2.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurface,
                shadowElevation = 10.dp,
                modifier = Modifier.fillMaxWidth(0.88f),
            ) {
                Column(Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 8.dp)) {
                    val actions = mapOf(
                        "copy" to MenuAction(addArrow("Kopieren"), R.drawable.mt_ic_copy, targetItem != null, onCopy),
                        "move" to MenuAction(addArrow("Verschieben"), R.drawable.mt_ic_move, targetItem != null, onMove),
                        "tools" to MenuAction("Tools", R.drawable.mt_ic_tools, targetItem != null, onTools),
                        "rename" to MenuAction("Umbenennen", R.drawable.mt_ic_rename, targetItem != null, onRename),
                        "delete" to MenuAction("Löschen", R.drawable.mt_ic_delete, targetItem != null, onDelete),
                        "compress" to MenuAction("Komprimieren", R.drawable.mt_ic_compress, targetItem != null, onCompress),
                        "properties" to MenuAction("Eigenschaften", R.drawable.mt_ic_properties, targetItem != null, onProperty),
                        "share" to MenuAction("Teilen", R.drawable.mt_ic_share, targetItem?.isDirectory == false, onShare),
                        "open_with" to MenuAction("Öffnen mit…", R.drawable.mt_ic_open_with, targetItem?.isDirectory == false, onOpenWith),
                        "bookmark" to MenuAction("Lesezeichen…", R.drawable.mt_ic_bookmark_add, targetItem != null, onAddBookmark),
                    )
                    prefs.fileMenuOrder.mapNotNull(actions::get).chunked(2).forEach { pair ->
                        ActionRow(
                            left = pair[0],
                            right = pair.getOrElse(1) { MenuAction("", R.drawable.mt_ic_open_with, false) {} },
                        )
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = { animateDismiss() }) { Text("SCHLIESSEN") }
                    }
                }
            }
        }
    }
}

private data class MenuAction(
    val title: String,
    @DrawableRes val iconRes: Int,
    val isEnabled: Boolean = true,
    val onClick: () -> Unit,
)

@Composable
private fun ActionRow(left: MenuAction, right: MenuAction) {
    Row(
        modifier = Modifier.fillMaxWidth().height(54.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ActionCell(left, Modifier.weight(1f))
        ActionCell(right, Modifier.weight(1f))
    }
}

@Composable
private fun ActionCell(action: MenuAction, modifier: Modifier = Modifier) {
    val alpha = if (action.isEnabled) 1f else 0.34f
    Row(
        modifier = modifier
            .fillMaxHeight()
            .clickable(enabled = action.isEnabled, onClick = action.onClick)
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(action.iconRes),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
            modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            action.title,
            fontSize = 16.sp,
            fontWeight = FontWeight.Normal,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
            maxLines = 1,
        )
    }
}
