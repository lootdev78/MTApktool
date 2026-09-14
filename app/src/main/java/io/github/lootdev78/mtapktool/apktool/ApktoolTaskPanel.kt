package io.github.lootdev78.mtapktool.apktool

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.filled.Check
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun ApktoolTaskPanel(
    visible: Boolean,
    jobs: List<ApktoolJobInfo>,
    onOpenJob: (String) -> Unit,
    onCancel: (String) -> Unit,
    onCancelAll: () -> Unit,
    onDismiss: () -> Unit,
) {
    if (!visible) return

    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(),
            exit = fadeOut(),
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
            enter = slideInHorizontally(initialOffsetX = { it }),
            exit = slideOutHorizontally(targetOffsetX = { it }),
            modifier = Modifier.align(Alignment.CenterEnd),
        ) {
            var drag by remember { mutableFloatStateOf(0f) }
            Surface(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(0.82f)
                    .pointerInput(onDismiss) {
                        detectHorizontalDragGestures(
                            onDragStart = { drag = 0f },
                            onHorizontalDrag = { change, amount ->
                                if (amount > 0) drag += amount
                                change.consume()
                            },
                            onDragEnd = {
                                if (drag > 100f) onDismiss()
                                drag = 0f
                            },
                            onDragCancel = { drag = 0f },
                        )
                    },
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 12.dp,
            ) {
                Column(Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = onDismiss) { Icon(Icons.Default.Check, contentDescription = "Schliessen") }
                        Text("Task", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.weight(1f))
                        if (jobs.any { !it.isTerminal }) {
                            TextButton(onClick = onCancelAll) { Text("ALLE STOPPEN") }
                        }
                    }

                    if (jobs.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                "No task information yet",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            items(jobs, key = { it.id }) { job ->
                                TaskCard(job = job, onOpen = { onOpenJob(job.id) }, onCancel = onCancel)
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
private fun TaskCard(job: ApktoolJobInfo, onOpen: () -> Unit, onCancel: (String) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen),
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
