package io.github.lootdev78.mtapktool.feature.explorer.component

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.DriveFileMove
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.lootdev78.mtapktool.feature.explorer.model.FileItem
import io.github.lootdev78.mtapktool.apktool.isApkLike
import io.github.lootdev78.mtapktool.apktool.isApktoolProject
import io.github.lootdev78.mtapktool.feature.explorer.viewmodel.ActivePane
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import kotlin.time.Duration.Companion.milliseconds

data class MenuAction(
    val title: String,
    val icon: ImageVector,
    val isEnabled: Boolean = true,
    val onClick: () -> Unit
)

@Composable
fun FileContextMenuDialog(
    targetItem: FileItem?,
    activePane: ActivePane,
    onDismissRequest: () -> Unit,
    onCopy: () -> Unit,
    onMove: () -> Unit,
    onLink: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onCompress: () -> Unit,
    onProperty: () -> Unit,
    onShare: () -> Unit,
    onAddBookmark: () -> Unit,
    onApktool: () -> Unit,
) {


    var isVisible by remember { mutableStateOf(false) }
    var isDeleteClicked by remember { mutableStateOf(false) }

    if (isDeleteClicked){
        ConfirmDialog(
            title = "Are you sure you want to delete this file?",
            subtitle = null,
            onConfirm = {
                onDelete()
                isDeleteClicked = false
            },
            onDismiss = {
                isDeleteClicked = false
            }
        )
    }
    val scope = rememberCoroutineScope()
    val targetFile = targetItem?.let { File(it.path) }
    val apktoolCapable = targetFile?.let { isApkLike(it) || isApktoolProject(it) } == true
    val apktoolTitle = if (targetFile?.isDirectory == true) "APK kompilieren" else "Apktool decodieren"

    val addArrow = fun(text: String): String{
        return if (activePane == ActivePane.LEFT){
            "$text ->"
        } else {
            "<- $text"
        }
    }

    val animateDismiss = {
        scope.launch {
            isVisible = false
            delay(150.milliseconds) // Wait for exit animation to finish
            onDismissRequest()
        }
    }

    LaunchedEffect(Unit) {
        isVisible = true // Trigger enter animation when dialog mounts
    }

    Dialog(
        onDismissRequest = { animateDismiss() },
        properties = DialogProperties(
            usePlatformDefaultWidth = false // Allows custom dialog sizing
        )
    ) {
        AnimatedVisibility(
            visible = isVisible,
            enter = scaleIn(
                initialScale = 0.85f,
                transformOrigin = TransformOrigin(0.5f, 0.5f),
                animationSpec = tween(durationMillis = 180)
            ) + fadeIn(animationSpec = tween(durationMillis = 180)),
            exit = scaleOut(
                targetScale = 0.85f,
                transformOrigin = TransformOrigin(0.5f, 0.5f),
                animationSpec = tween(durationMillis = 150)
            ) + fadeOut(animationSpec = tween(durationMillis = 150))
        ) {
            Surface(
                shape = RoundedCornerShape(2.dp),
                color = MaterialTheme.colorScheme.surface,
                contentColor =  MaterialTheme.colorScheme.onSurface,
                shadowElevation = 8.dp,
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .padding(8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp, horizontal = 8.dp)
                ) {
                    // Grid Rows
                    ActionRow(
                        left = MenuAction(
                            addArrow("Copy"),
                            Icons.Outlined.ContentCopy,
                            isEnabled = true,
                            onClick = onCopy
                        ),
                        right = MenuAction(
                            addArrow("Move"),
                            Icons.AutoMirrored.Outlined.DriveFileMove,
                            isEnabled = true,
                            onClick = onMove
                        )
                    )

                    ActionRow(
                        left = MenuAction(
                            addArrow("Link"),
                            Icons.Outlined.Link,
                            isEnabled = false,
                            onClick = onLink
                        ),
                        right = MenuAction(
                            "Rename",
                            Icons.Default.Edit,
                            isEnabled = true,
                            onClick = onRename
                        )
                    )

                    ActionRow(
                        left = MenuAction(
                            "Delete",
                            Icons.Default.Delete,
                            isEnabled = true,
                            onClick = {
                                isDeleteClicked = true
                            }
                        ),
                        right = MenuAction(
                            "Compress",
                            Icons.Default.Archive,
                            isEnabled = false,
                            onClick = onCompress
                        ) // Unhighlighted
                    )

                    ActionRow(
                        left = MenuAction(
                            "Property",
                            Icons.Outlined.Info,
                            isEnabled = true,
                            onClick = onProperty
                        ),
                        right = MenuAction(
                            "Share",
                            Icons.Default.Share,
                            isEnabled = targetItem?.isDirectory == false,
                            onClick = onShare
                        )
                    )

                    ActionRow(
                        left = MenuAction(
                            if (apktoolCapable) apktoolTitle else "Open with...",
                            if (apktoolCapable) Icons.Default.Build else Icons.Default.Check,
                            isEnabled = apktoolCapable,
                            onClick = onApktool),
                        right = MenuAction(
                            "+ Bookmarks",
                            Icons.Outlined.BookmarkAdd,
                            isEnabled = false,
                            onClick = onAddBookmark
                        ) // Unhighlighted
                    )
                }
            }
        }
    }
}

@Composable
private fun ActionRow(
    left: MenuAction,
    right: MenuAction
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ActionCell(action = left, modifier = Modifier.weight(1f))
        ActionCell(action = right, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun ActionCell(
    action: MenuAction,
    modifier: Modifier = Modifier
) {
    val alpha = if (action.isEnabled) 1.0f else 0.35f // Dimmed appearance when unhighlighted
    val textColor = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha)
    val iconColor = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha)

    Row(
        modifier = modifier
            .fillMaxHeight()
            .clickable(enabled = action.isEnabled) { action.onClick() }
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = action.icon,
            contentDescription = action.title,
            tint = iconColor,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = action.title,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = textColor
        )
    }
}