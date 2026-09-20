package io.github.lootdev78.mtapktool.apktool

import android.os.Environment
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.InstallMobile
import androidx.compose.material.icons.filled.MergeType
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.lootdev78.mtapktool.feature.explorer.viewmodel.ActivePane
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Compact MTApktool-style split package workflow. It intentionally stays a dialog. */
@Composable
fun SplitPackageDialog(
    file: File,
    sourcePane: ActivePane,
    leftPath: String,
    rightPath: String,
    onDismiss: () -> Unit,
    onDecode: () -> Unit,
    onOutputCreated: (File, ActivePane) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var entries by remember(file.absolutePath, file.lastModified()) { mutableStateOf<List<SplitArchiveSupport.ApkEntry>>(emptyList()) }
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
    var scanning by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    var showMerge by remember { mutableStateOf(false) }

    LaunchedEffect(file.absolutePath, file.lastModified()) {
        scanning = true
        val result = withContext(Dispatchers.IO) { runCatching { SplitArchiveSupport.inspect(file) } }
        entries = result.getOrDefault(emptyList())
        selected = entries.map { it.path }.toSet()
        scanning = false
        result.exceptionOrNull()?.let { Toast.makeText(context, it.message ?: "Split scan failed", Toast.LENGTH_LONG).show() }
    }

    val chosen = entries.filter { it.path in selected }
    Dialog(
        onDismissRequest = { if (!busy) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.9f),
            shape = RoundedCornerShape(2.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            tonalElevation = 0.dp,
        ) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp)) {
                Text(file.name, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${file.extension.uppercase()} · ${entries.size} APKs · ${selected.size} gewählt",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp, bottom = 12.dp),
                )

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        modifier = Modifier.weight(1f),
                        enabled = !busy && chosen.isNotEmpty(),
                        onClick = {
                            busy = true
                            status = "Installation vorbereiten …"
                            scope.launch {
                                val result = withContext(Dispatchers.IO) { runCatching { SplitPackageActions.install(context, file, chosen) } }
                                busy = false
                                result.onSuccess { Toast.makeText(context, "Installer geöffnet", Toast.LENGTH_SHORT).show() }
                                    .onFailure { Toast.makeText(context, it.message ?: "Install failed", Toast.LENGTH_LONG).show() }
                            }
                        },
                    ) {
                        Icon(Icons.Default.InstallMobile, null)
                        Spacer(Modifier.width(6.dp))
                        Text("INSTALL", maxLines = 1)
                    }
                    Button(
                        modifier = Modifier.weight(1f),
                        enabled = !busy && chosen.isNotEmpty(),
                        onClick = { showMerge = true },
                    ) {
                        Icon(Icons.Default.MergeType, null)
                        Spacer(Modifier.width(6.dp))
                        Text("ZU APK", maxLines = 1)
                    }
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    TextButton(onClick = {
                        val preferred = entries.firstOrNull { it.preferred }
                        selected = entries.filter { it.preferred || it.path.contains("config", true) }.map { it.path }.toSet()
                            .ifEmpty { preferred?.let { setOf(it.path) } ?: emptySet() }
                    }) { Text("AUTO", maxLines = 1) }
                    TextButton(onClick = { selected = entries.map { it.path }.toSet() }) { Text("ALLE", maxLines = 1) }
                    TextButton(onClick = { selected = entries.firstOrNull { it.preferred }?.let { setOf(it.path) } ?: emptySet() }) { Text("NUR BASE", maxLines = 1) }
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(
                        modifier = Modifier.weight(1f),
                        enabled = entries.isNotEmpty() && !busy,
                        onClick = {
                            val base = entries.firstOrNull { it.preferred } ?: entries.first()
                            busy = true
                            status = "Base extrahieren …"
                            scope.launch {
                                val outDir = File(file.parentFile, file.nameWithoutExtension + "_extracted")
                                val result = withContext(Dispatchers.IO) { runCatching { SplitPackageActions.extract(file, listOf(base), outDir).first() } }
                                busy = false
                                result.onSuccess { onOutputCreated(it, sourcePane) }
                                    .onFailure { Toast.makeText(context, it.message ?: "Extract failed", Toast.LENGTH_LONG).show() }
                            }
                        },
                    ) { Text("BASE EXTRAHIEREN", maxLines = 1) }
                    TextButton(
                        modifier = Modifier.weight(1f),
                        enabled = chosen.isNotEmpty() && !busy,
                        onClick = {
                            busy = true
                            status = "Auswahl extrahieren …"
                            scope.launch {
                                val outDir = File(file.parentFile, file.nameWithoutExtension + "_extracted")
                                val result = withContext(Dispatchers.IO) { runCatching { SplitPackageActions.extract(file, chosen, outDir) } }
                                busy = false
                                result.onSuccess { files -> files.firstOrNull()?.let { onOutputCreated(it, sourcePane) } }
                                    .onFailure { Toast.makeText(context, it.message ?: "Extract failed", Toast.LENGTH_LONG).show() }
                            }
                        },
                    ) { Text("AUSWAHL EXTRAHIEREN", maxLines = 1) }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { onDismiss(); onDecode() }, enabled = !busy) { Text("DEKOMPILIEREN", maxLines = 1) }
                }

                if (busy) {
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(10.dp))
                        Text(status, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }

                if (scanning) {
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(10.dp))
                        Text("APKs werden gelesen …")
                    }
                } else {
                    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 300.dp)) {
                        items(entries, key = { it.path }) { entry ->
                            Row(
                                Modifier.fillMaxWidth().clickable {
                                    selected = selected.toMutableSet().also { if (!it.add(entry.path)) it.remove(entry.path) }
                                }.padding(vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(checked = entry.path in selected, onCheckedChange = { checked ->
                                    selected = selected.toMutableSet().also { if (checked) it.add(entry.path) else it.remove(entry.path) }
                                })
                                Icon(Icons.Default.Archive, null, modifier = Modifier.size(22.dp))
                                Spacer(Modifier.width(8.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(entry.displayName, fontWeight = if (entry.preferred) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        buildString {
                                            append(String.format("%.2f MiB", entry.sizeMiB))
                                            if (entry.preferred) append(" · Base/Universal")
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss, enabled = !busy) { Text("SCHLIESSEN") }
                }
            }
        }
    }

    if (showMerge) {
        SplitMergeDialog(
            file = file,
            entries = chosen,
            sourcePane = sourcePane,
            leftPath = leftPath,
            rightPath = rightPath,
            onDismiss = { showMerge = false },
            onCreated = { output, pane -> showMerge = false; onOutputCreated(output, pane) },
        )
    }
}

@Composable
private fun SplitMergeDialog(
    file: File,
    entries: List<SplitArchiveSupport.ApkEntry>,
    sourcePane: ActivePane,
    leftPath: String,
    rightPath: String,
    onDismiss: () -> Unit,
    onCreated: (File, ActivePane) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val defaultDir = file.parentFile ?: File(Environment.getExternalStorageDirectory(), "apktool/apks")
    var output by remember(file) { mutableStateOf(File(defaultDir, file.nameWithoutExtension + ".merged.apk").absolutePath) }
    var outputPane by remember { mutableStateOf(sourcePane) }
    var compression by remember { mutableStateOf("6") }
    var force by remember { mutableStateOf(false) }
    var autoSign by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }

    fun choose(path: String, pane: ActivePane) {
        if (path.startsWith("content://")) return
        output = File(path, file.nameWithoutExtension + ".merged.apk").absolutePath
        outputPane = pane
    }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("AntiSplit-M · Zu APK") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text("${entries.size} APK(s) ausgewählt", style = MaterialTheme.typography.bodySmall)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    TextButton(onClick = { choose(defaultDir.absolutePath, sourcePane) }) { Text("GLEICHER") }
                    TextButton(onClick = { choose(leftPath, ActivePane.LEFT) }, enabled = !leftPath.startsWith("content://")) { Text("LINKS") }
                    TextButton(onClick = { choose(rightPath, ActivePane.RIGHT) }, enabled = !rightPath.startsWith("content://")) { Text("RECHTS") }
                }
                OutlinedTextField(value = output, onValueChange = { output = it }, label = { Text("Ausgabe-APK") }, singleLine = true, modifier = Modifier.fillMaxWidth(), enabled = !busy)
                OutlinedTextField(value = compression, onValueChange = { compression = it.filter(Char::isDigit).take(1) }, label = { Text("Kompression 0–9") }, singleLine = true, modifier = Modifier.fillMaxWidth(), enabled = !busy)
                Row(Modifier.fillMaxWidth().clickable { force = !force }, verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(force, { force = it }); Text("Force Merge")
                }
                Row(Modifier.fillMaxWidth().clickable { autoSign = !autoSign }, verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(autoSign, { autoSign = it })
                    Column {
                        Text("Automatisch signieren")
                        Text(
                            ApktoolSettings.signatureLabel(ApktoolSettings.signatureDefaults(context)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (busy) Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp); Spacer(Modifier.width(8.dp)); Text(status, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("ABBRECHEN") } },
        confirmButton = {
            TextButton(
                enabled = !busy && entries.isNotEmpty() && output.isNotBlank(),
                onClick = {
                    busy = true; status = "AntiSplit-M …"
                    scope.launch {
                        val out = File(output)
                        val result = withContext(Dispatchers.IO) {
                            runCatching {
                                out.parentFile?.mkdirs()
                                val merged = SplitPackageActions.mergeToApk(file, entries, out, compression.toIntOrNull() ?: 6, force) { line -> scope.launch { status = line } }
                                if (autoSign) SplitPackageActions.signMergedApk(context, merged) { line -> scope.launch { status = line } } else merged
                            }
                        }
                        busy = false
                        result.onSuccess { onCreated(it, outputPane) }
                            .onFailure { Toast.makeText(context, it.message ?: "Merge failed", Toast.LENGTH_LONG).show() }
                    }
                },
            ) { Text("ERSTELLEN") }
        },
    )
}
