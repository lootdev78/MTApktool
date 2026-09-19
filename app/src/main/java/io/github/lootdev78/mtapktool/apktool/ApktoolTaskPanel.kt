package io.github.lootdev78.mtapktool.apktool

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.lootdev78.mtapktool.core.i18n.UiText
import io.github.lootdev78.mtapktool.archive.ArchiveTaskInfo
import io.github.lootdev78.mtapktool.archive.ExplorerTaskKind
import kotlinx.coroutines.launch
import kotlin.math.abs

@Composable
fun ApktoolTaskPanel(
    visible: Boolean,
    jobs: List<ApktoolJobInfo>,
    archiveTasks: List<ArchiveTaskInfo>,
    onOpenJob: (String) -> Unit,
    onCancel: (String) -> Unit,
    onCancelAll: () -> Unit,
    onCancelArchive: (String) -> Unit,
    onCancelAllArchive: () -> Unit,
    onRemove: (String) -> Unit,
    onClearFinished: () -> Unit,
    onDismiss: () -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(180)),
            exit = fadeOut(tween(160)),
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.48f))
                    .clickable(onClick = onDismiss),
            )
        }

        AnimatedVisibility(
            visible = visible,
            enter = slideInHorizontally(animationSpec = tween(220), initialOffsetX = { it }),
            exit = slideOutHorizontally(animationSpec = tween(200), targetOffsetX = { it }),
            modifier = Modifier.align(Alignment.CenterEnd),
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(0.72f),
                color = MaterialTheme.colorScheme.background,
                shadowElevation = 10.dp,
            ) {
                Column(Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant).padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = onDismiss) { Icon(Icons.Default.Check, contentDescription = "Schliessen") }
                        Column(Modifier.weight(1f)) {
                            Text(UiText.t("Tasks", "Aufgaben"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Normal)
                            val active = jobs.count { !it.isTerminal } + archiveTasks.size
                            if (active > 0) Text(UiText.t("$active active", "$active aktiv"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (jobs.any { it.isTerminal }) {
                            IconButton(onClick = onClearFinished) {
                                Icon(Icons.Default.DeleteSweep, contentDescription = UiText.t("Clear finished tasks", "Fertige Aufgaben leeren"))
                            }
                        }
                        if (jobs.any { !it.isTerminal } || archiveTasks.isNotEmpty()) {
                            TextButton(onClick = { onCancelAll(); onCancelAllArchive() }) { Text(UiText.t("STOP ALL", "ALLE STOPPEN")) }
                        }
                    }

                    if (jobs.isEmpty() && archiveTasks.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                UiText.t("No task information yet", "Noch keine Aufgabeninformationen"),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(0.dp),
                        ) {
                            items(archiveTasks, key = { it.id }) { task ->
                                ArchiveTaskCard(task = task, onCancel = onCancelArchive)
                            }
                            items(jobs, key = { it.id }) { job ->
                                TaskCard(
                                    job = job,
                                    onOpen = { onOpenJob(job.id) },
                                    onCancel = onCancel,
                                    onRemove = onRemove,
                                )
                            }
                            item { Spacer(Modifier.height(24.dp)) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ArchiveTaskCard(task: ArchiveTaskInfo, onCancel: (String) -> Unit) {
    Surface(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = when (task.kind) {
                        ExplorerTaskKind.ARCHIVE_CREATE -> Icons.Default.Archive
                        ExplorerTaskKind.ARCHIVE_EXTRACT -> Icons.Default.Unarchive
                        ExplorerTaskKind.ARCHIVE_UPDATE -> Icons.Default.EditNote
                        ExplorerTaskKind.COPY -> Icons.Default.ContentCopy
                        ExplorerTaskKind.MOVE -> Icons.Default.DriveFileMove
                        ExplorerTaskKind.DELETE -> Icons.Default.Delete
                    },
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(UiText.auto(task.title), fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(task.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(io.github.lootdev78.mtapktool.core.i18n.UiText.auto("${task.progress ?: 0}%"), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.height(7.dp))
            LinearProgressIndicator(
                progress = { ((task.progress ?: 0).coerceIn(0, 100)) / 100f },
                modifier = Modifier.fillMaxWidth().height(2.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.outlineVariant,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { onCancel(task.id) }) { Text(UiText.t("STOP", "STOPPEN")) }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
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

    Surface(
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
        color = MaterialTheme.colorScheme.background,
        tonalElevation = 0.dp,
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    job.title,
                    modifier = Modifier.weight(1f),
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.width(8.dp))
                Text(job.statusLabel(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }
            if (!job.isTerminal) {
                Spacer(Modifier.height(7.dp))
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().height(2.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.outlineVariant,
                )
            }
            if (job.line.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    job.line.lineSequence().lastOrNull().orEmpty(),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!job.output.isNullOrBlank()) {
                Text(
                    job.output.orEmpty(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onOpen) { Text(UiText.t("DETAILS", "DETAILS")) }
                if (!job.isTerminal) {
                    TextButton(onClick = { onCancel(job.id) }) { Text(UiText.t("STOP", "STOPPEN")) }
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
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
    val text = job?.let { it.log.ifBlank { it.line } }.orEmpty().ifBlank { UiText.t("Job is starting…", "Aufgabe wird gestartet…") }
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
                TextButton(onClick = { onCancel(jobId) }) { Text(UiText.t("CANCEL", "ABBRECHEN")) }
            }
        },
        confirmButton = {
            TextButton(onClick = onHide) { Text(if (job?.isTerminal == true) UiText.t("CLOSE", "SCHLIESSEN") else UiText.t("HIDE", "VERSTECKEN")) }
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
