package io.github.lootdev78.mtapktool.apktool

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun ApktoolTaskPanel(
    visible: Boolean,
    jobs: List<ApktoolJobInfo>,
    dragProgress: Float = if (visible) 1f else 0f,
    dragging: Boolean = false,
    onDragProgress: (Float) -> Unit = {},
    onDragSettled: (Boolean) -> Unit = {},
    onOpenJob: (String) -> Unit,
    onCancel: (String) -> Unit,
    onCancelAll: () -> Unit,
    onRemove: (String) -> Unit,
    onClearFinished: () -> Unit,
    onDismiss: () -> Unit,
) {
    val drawerAnimation = remember { Animatable(if (visible) 1f else 0f) }
    var previousDragging by remember { mutableStateOf(false) }
    val latestDragProgress by rememberUpdatedState(dragProgress.coerceIn(0f, 1f))
    LaunchedEffect(dragging, visible) {
        val endedDrag = previousDragging && !dragging
        previousDragging = dragging
        if (dragging) return@LaunchedEffect
        if (endedDrag) drawerAnimation.snapTo(latestDragProgress)
        drawerAnimation.animateTo(
            targetValue = if (visible) 1f else 0f,
            animationSpec = tween(if (visible) 220 else 190),
        )
    }
    val progress = (if (dragging) latestDragProgress else drawerAnimation.value).coerceIn(0f, 1f)
    if (progress <= 0.001f && !visible && !dragging) return

    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.48f * progress))
                .clickable(enabled = progress > 0.15f, onClick = onDismiss),
        )

        var panelWidth by remember { mutableIntStateOf(1) }
        val currentProgress by rememberUpdatedState(progress)
        Surface(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .fillMaxWidth(0.82f)
                .onSizeChanged { panelWidth = it.width.coerceAtLeast(1) }
                .offset { IntOffset(((1f - progress) * panelWidth).roundToInt(), 0) }
                .pointerInput(visible, panelWidth) {
                    var start = 1f
                    var lastTime = 0L
                    var velocityX = 0f
                    detectHorizontalDragGestures(
                        onDragStart = {
                            start = currentProgress
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
                            start = (start - amount / panelWidth.toFloat()).coerceIn(0f, 1f)
                            onDragProgress(start)
                        },
                        onDragEnd = {
                            val open = when {
                                velocityX < -900f -> true
                                velocityX > 900f -> false
                                else -> start >= 0.55f
                            }
                            onDragSettled(open)
                        },
                        onDragCancel = { onDragSettled(start >= 0.55f) },
                    )
                },
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 12.dp,
        ) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Check, contentDescription = "Schliessen") }
                    Column(Modifier.weight(1f)) {
                        Text("Tasks", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                        val active = jobs.count { !it.isTerminal }
                        if (active > 0) Text("$active aktiv", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (jobs.any { it.isTerminal }) {
                        TextButton(onClick = onClearFinished) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text("LISTE LEEREN")
                        }
                    }
                    if (jobs.any { !it.isTerminal }) TextButton(onClick = onCancelAll) { Text("ALLE STOPPEN") }
                }

                if (jobs.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Noch keine Task-Informationen", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                    }
                } else {
                    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(jobs, key = { it.id }) { job ->
                            TaskCard(job = job, onOpen = { onOpenJob(job.id) }, onCancel = onCancel, onRemove = onRemove)
                        }
                        item { Spacer(Modifier.height(24.dp)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun TaskCard(
    job: ApktoolJobInfo,
    onOpen: () -> Unit,
    onCancel: (String) -> Unit,
    onRemove: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val offset = remember(job.id) { Animatable(0f) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { translationX = offset.value }
            .pointerInput(job.id, job.isTerminal) {
                if (!job.isTerminal) return@pointerInput
                detectHorizontalDragGestures(
                    onHorizontalDrag = { change, amount ->
                        change.consume()
                        scope.launch { offset.snapTo((offset.value + amount).coerceIn(-360f, 360f)) }
                    },
                    onDragEnd = {
                        scope.launch {
                            if (abs(offset.value) >= 120f) {
                                val target = if (offset.value < 0) -size.width.toFloat() else size.width.toFloat()
                                offset.animateTo(target, tween(150))
                                onRemove(job.id)
                            } else {
                                offset.animateTo(0f, spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium))
                            }
                        }
                    },
                    onDragCancel = { scope.launch { offset.animateTo(0f) } },
                )
            }
            .clickable(onClick = onOpen),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    job.title,
                    modifier = Modifier.weight(1f),
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.width(8.dp))
                Text(job.statusLabel(), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            }
            if (job.command.isNotBlank()) {
                Text(
                    job.command,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (job.line.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    job.line.lineSequence().lastOrNull().orEmpty(),
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onOpen) { Text("OUTPUT") }
                if (!job.isTerminal) {
                    TextButton(onClick = { onCancel(job.id) }) {
                        Icon(Icons.Default.Stop, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("STOP")
                    }
                } else {
                    TextButton(onClick = { onRemove(job.id) }) { Text("ENTFERNEN") }
                }
            }
        }
    }
}

/**
 * Live Apktool output. Dismissing/hiding this dialog never cancels the job.
 * The same job can be reopened from the right Task panel.
 */
@Composable
fun ApktoolJobOutputDialog(
    jobId: String,
    job: ApktoolJobInfo?,
    onHide: () -> Unit,
    onCancel: (String) -> Unit,
) {
    val scroll = rememberScrollState()
    val text = job?.let { it.log.ifBlank { it.line } }.orEmpty().ifBlank { "Job wird gestartet …" }
    LaunchedEffect(text) {
        if (scroll.maxValue > 0) scroll.scrollTo(scroll.maxValue)
    }

    AlertDialog(
        onDismissRequest = onHide,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Settings, contentDescription = null)
                Spacer(Modifier.width(10.dp))
                Text(job?.dialogTitle() ?: "Apktool…", maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        },
        text = {
            Column {
                if (job != null) {
                    Text(
                        job.statusLabel(),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                }
                Surface(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 180.dp, max = 430.dp),
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF303030),
                ) {
                    Text(
                        text,
                        modifier = Modifier.padding(12.dp).verticalScroll(scroll),
                        style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                        color = Color(0xFF65F26B),
                    )
                }
                if (!job?.output.isNullOrBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        job?.output.orEmpty(),
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        },
        dismissButton = {
            if (job != null && !job.isTerminal) {
                TextButton(onClick = { onCancel(jobId) }) { Text("ABBRECHEN") }
            }
        },
        confirmButton = {
            TextButton(onClick = onHide) { Text(if (job?.isTerminal == true) "SCHLIESSEN" else "VERSTECKEN") }
        },
    )
}

private fun ApktoolJobInfo.statusLabel(): String = when (status) {
    "QUEUED" -> "WARTET"
    "RUNNING" -> "LÄUFT"
    "SUCCEEDED" -> "FERTIG"
    "FAILED" -> "FEHLER"
    "CANCELLED" -> "ABGEBROCHEN"
    else -> status
}

private fun ApktoolJobInfo.dialogTitle(): String = when {
    title.startsWith("Decode ", ignoreCase = true) -> "Dekompilieren…"
    title.startsWith("Build ", ignoreCase = true) -> "Erstellen…"
    title.startsWith("Framework ", ignoreCase = true) -> "Framework installieren…"
    else -> title
}
