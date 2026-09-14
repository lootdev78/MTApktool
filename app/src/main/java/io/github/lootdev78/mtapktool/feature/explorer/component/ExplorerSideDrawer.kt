package io.github.lootdev78.mtapktool.feature.explorer.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * Interactive left navigation drawer. During a drag the panel is bound directly to the finger.
 * When the finger is released, animation continues from the exact release position rather than
 * restarting from an endpoint. The right task drawer uses the same model.
 */
@Composable
fun ExplorerSideDrawer(
    visible: Boolean,
    drawerWidth: Dp,
    dragProgress: Float = if (visible) 1f else 0f,
    dragging: Boolean = false,
    onDragProgress: (Float) -> Unit = {},
    onDragSettled: (Boolean) -> Unit = {},
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    val animation = remember { Animatable(if (visible) 1f else 0f) }
    var previousDragging by remember { mutableStateOf(false) }
    val latestDragProgress by rememberUpdatedState(dragProgress.coerceIn(0f, 1f))

    LaunchedEffect(dragging, visible) {
        val endedDrag = previousDragging && !dragging
        previousDragging = dragging
        if (dragging) return@LaunchedEffect
        if (endedDrag) animation.snapTo(latestDragProgress)
        animation.animateTo(
            targetValue = if (visible) 1f else 0f,
            animationSpec = tween(if (visible) 220 else 190),
        )
    }

    val progress = (if (dragging) latestDragProgress else animation.value).coerceIn(0f, 1f)
    if (progress <= 0.001f && !visible && !dragging) return

    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.42f * progress))
                .clickable(enabled = progress > 0.15f, onClick = onDismiss),
        )

        var panelWidth by remember { mutableIntStateOf(1) }
        val currentProgress by rememberUpdatedState(progress)
        Surface(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .fillMaxHeight()
                .width(drawerWidth)
                .onSizeChanged { panelWidth = it.width.coerceAtLeast(1) }
                .offset { IntOffset((-((1f - progress) * panelWidth)).roundToInt(), 0) }
                .pointerInput(visible, panelWidth) {
                    var p = currentProgress
                    var lastTime = 0L
                    var velocityX = 0f
                    detectHorizontalDragGestures(
                        onDragStart = {
                            p = currentProgress
                            lastTime = 0L
                            velocityX = 0f
                        },
                        onHorizontalDrag = { change, amount ->
                            change.consume()
                            if (lastTime != 0L) {
                                val dt = (change.uptimeMillis - lastTime).coerceAtLeast(1L)
                                val instantaneous = amount * 1000f / dt.toFloat()
                                velocityX = velocityX * 0.55f + instantaneous * 0.45f
                            }
                            lastTime = change.uptimeMillis
                            p = (p + amount / panelWidth.toFloat()).coerceIn(0f, 1f)
                            onDragProgress(p)
                        },
                        onDragEnd = {
                            val open = when {
                                velocityX > 900f -> true
                                velocityX < -900f -> false
                                else -> p >= 0.5f
                            }
                            onDragSettled(open)
                        },
                        onDragCancel = { onDragSettled(p >= 0.5f) },
                    )
                },
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 12.dp,
        ) {
            Box(Modifier.fillMaxHeight()) { content() }
        }
    }
}
