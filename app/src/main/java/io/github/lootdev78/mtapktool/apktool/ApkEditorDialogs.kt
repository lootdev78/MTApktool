package io.github.lootdev78.mtapktool.apktool

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import io.github.lootdev78.mtapktool.core.theme.MtClassicAlertDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

@Composable
fun ApkEditorDialog(file: File, action: ApkEditorAction, onDismiss: () -> Unit, onJobQueued: (String) -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("apk_editor_options", 0) }
    var output by remember(file, action) { mutableStateOf(File(file.parentFile, file.nameWithoutExtension + action.suffix + ".apk").path) }
    var overwrite by remember { mutableStateOf(false) }
    var autoSign by remember { mutableStateOf(prefs.getBoolean("auto_sign", true)) }
    var align by remember { mutableStateOf(prefs.getBoolean("align", true)) }
    var killMethod by remember { mutableStateOf(prefs.getString("kill_method", "MT") ?: "MT") }
    var publicXml by remember { mutableStateOf("") }
    var fixTypes by remember { mutableStateOf(prefs.getBoolean("fix_types", true)) }
    var cleanMeta by remember { mutableStateOf(prefs.getBoolean("clean_meta", true)) }
    var skipManifest by remember { mutableStateOf(prefs.getBoolean("skip_manifest", false)) }
    var confuseZip by remember { mutableStateOf(false) }
    var dexLevel by remember { mutableStateOf(prefs.getInt("dex_level", 0).coerceIn(0, 1)) }
    var deepOptimize by remember { mutableStateOf(prefs.getBoolean("deep_optimize", false)) }
    var preserveDebug by remember { mutableStateOf(prefs.getBoolean("preserve_debug", true)) }
    var passes by remember { mutableStateOf(prefs.getInt("max_passes", 5).toString()) }
    var deleteFiles by remember { mutableStateOf(prefs.getBoolean("delete_files", false)) }
    var patterns by remember { mutableStateOf(prefs.getString("delete_patterns", "DebugProbesKt\\.bin\n.*\\.properties") ?: "") }
    var showSignSettings by remember { mutableStateOf(false) }
    var showDeleteList by remember { mutableStateOf(false) }
    var picking by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val xmlPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            picking = true
            val result = withContext(Dispatchers.IO) { runCatching {
                val directory = File(context.cacheDir, "apk-editor-imports").apply { mkdirs() }
                val target = File(directory, "${UUID.randomUUID()}-public.xml")
                try {
                    context.contentResolver.openInputStream(uri)?.use { input -> target.outputStream().use { out ->
                        val buffer = ByteArray(8192); var total = 0L
                        while (true) { val read = input.read(buffer); if (read < 0) break; total += read; check(total <= 16L * 1024 * 1024) { "public.xml ist zu groß" }; out.write(buffer, 0, read) }
                    } } ?: throw java.io.IOException("Datei nicht lesbar")
                    target.path
                } catch (failure: Exception) { target.delete(); throw failure }
            } }
            picking = false
            result.onSuccess { publicXml = it }.onFailure { error = it.message }
        }
    }

    MtClassicAlertDialog(
        onDismissRequest = { if (!picking) onDismiss() },
        title = { Text(action.title) },
        text = {
            Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(file.name, style = MaterialTheme.typography.titleSmall)
                when (action) {
                    ApkEditorAction.KILL_SIGNATURE -> {
                        Text("MT übernimmt das Originalzertifikat in einen Application-Hook. RePairip passt bekannte Pairip-Prüfmethoden an.", style = MaterialTheme.typography.bodySmall)
                        EditorChoice("Methode", killMethod, listOf("MT", "RePairip")) { killMethod = it }
                    }
                    ApkEditorAction.REFACTOR -> {
                        Text("Obfuskierte Ressourcen-Namen und Dateipfade wieder lesbar machen. Optional Namen aus public.xml zuweisen.", style = MaterialTheme.typography.bodySmall)
                        OutlinedTextField(publicXml, { publicXml = it }, label = { Text("public.xml (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        TextButton(enabled = !picking, onClick = { xmlPicker.launch(arrayOf("text/xml", "application/xml", "*/*")) }) { Text(if (picking) "DATEI WIRD GELESEN…" else "PUBLIC.XML AUSWÄHLEN") }
                        EditorToggle("Ressourcen-Typnamen korrigieren", fixTypes) { fixTypes = it }
                        EditorToggle("META-INF bereinigen", cleanMeta) { cleanMeta = it }
                    }
                    ApkEditorAction.OPTIMIZE -> {
                        Text("ZIP neu packen, Ressourcen und native Bibliotheken passend speichern. Alte Signaturen werden entfernt.", style = MaterialTheme.typography.bodySmall)
                        EditorToggle("Ausgewählte Dateien entfernen", deleteFiles) { deleteFiles = it }
                        TextButton(onClick = { showDeleteList = true }) { Text("DATEILISTE BEARBEITEN") }
                        EditorToggle("DEX zusätzlich optimieren", deepOptimize) { deepOptimize = it }
                        if (deepOptimize) {
                            Text("Bereinigt unbenutzte und doppelte DEX-Daten. Klassen und Methoden bleiben erhalten.", style = MaterialTheme.typography.bodySmall)
                            EditorToggle("Debug-Informationen behalten", preserveDebug) { preserveDebug = it }
                            OutlinedTextField(passes, { passes = it.filter(Char::isDigit).take(2) }, label = { Text("Maximale Durchläufe (1–25)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.fillMaxWidth())
                        }
                    }
                    ApkEditorAction.PROTECT -> {
                        EditorToggle("Manifest-Schutz überspringen", skipManifest) { skipManifest = it }
                        EditorToggle("ZIP-Struktur verwirren", confuseZip) { confuseZip = it; if (it) { autoSign = false; align = false } }
                        EditorChoice("DEX-Schutzlevel", dexLevel.toString(), listOf("0", "1")) { dexLevel = it.toInt() }
                        Text("Level 0: Ressourcen-Schutz. Level 1: zusätzlich DEX-Array-Payloads. ZIP-Verwirrung erzeugt eine unsignierte Datei und verhindert die normale Signier-/Zipalign-Pipeline.", style = MaterialTheme.typography.bodySmall)
                    }
                }
                OutlinedTextField(output, { output = it }, label = { Text("Ausgabedatei") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                EditorToggle("Bestehende Ausgabedatei überschreiben", overwrite) { overwrite = it }
                EditorToggle("Zipalign (16-KiB-Ausrichtung)", align, !confuseZip) { align = it }
                EditorToggle("Automatisch signieren", autoSign, !confuseZip) { autoSign = it }
                TextButton(onClick = { showSignSettings = true }) { Text("SCHLÜSSEL & SIGNATUREINSTELLUNGEN") }
                if (!autoSign) Text("Die Ausgabe muss vor der Installation signiert werden.", style = MaterialTheme.typography.bodySmall)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        dismissButton = { TextButton(enabled = !picking, onClick = onDismiss) { Text("ABBRECHEN") } },
        confirmButton = { TextButton(enabled = !picking, onClick = {
            runCatching {
                val target = File(output.trim())
                require(target.isAbsolute && target.extension.equals("apk", true)) { "Eine absolute .apk-Ausgabedatei angeben" }
                require(target.canonicalFile != file.canonicalFile) { "Die Originaldatei darf nicht überschrieben werden" }
                require(!target.exists() || overwrite) { "Die Ausgabedatei existiert bereits" }
                val count = passes.toIntOrNull() ?: 0
                require(!deepOptimize || count in 1..25) { "1 bis 25 Durchläufe angeben" }
                val removals = if (deleteFiles) patterns.lines().map(String::trim).filter(String::isNotEmpty) else emptyList()
                removals.forEach { Regex(it) }
                require(publicXml.isBlank() || File(publicXml).isFile) { "public.xml nicht gefunden" }
                prefs.edit().putBoolean("auto_sign", autoSign).putBoolean("align", align).putString("kill_method", killMethod)
                    .putBoolean("fix_types", fixTypes).putBoolean("clean_meta", cleanMeta).putBoolean("skip_manifest", skipManifest)
                    .putInt("dex_level", dexLevel).putBoolean("deep_optimize", deepOptimize).putBoolean("preserve_debug", preserveDebug)
                    .putInt("max_passes", count.coerceIn(1, 25)).putBoolean("delete_files", deleteFiles).putString("delete_patterns", patterns).apply()
                val request = ApkEditorRequest(action, file.path, target.path, overwrite, killMethod, publicXml,
                    fixTypes, cleanMeta, skipManifest, confuseZip, dexLevel, deepOptimize, preserveDebug, count.coerceIn(1, 25), removals)
                ApktoolJobService.enqueueEditor(context, request, align && !confuseZip, autoSign && !confuseZip)
            }.onSuccess { onJobQueued(it); onDismiss() }.onFailure { error = it.message ?: "Auftrag konnte nicht gestartet werden" }
        }) { Text("STARTEN") } },
    )
    if (showSignSettings) SignatureManagerDialog { showSignSettings = false }
    if (showDeleteList) {
        var draft by remember { mutableStateOf(patterns) }
        MtClassicAlertDialog(onDismissRequest = { showDeleteList = false }, title = { Text("Dateien entfernen") }, text = {
            Column {
                Text("Je Zeile ein regulärer Ausdruck für den vollständigen APK-Pfad. Manifest, resources.arsc, Haupt-DEX und native Bibliotheken bleiben erhalten.", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(draft, { draft = it }, label = { Text("APK-Pfade / reguläre Ausdrücke") }, minLines = 5, maxLines = 10, modifier = Modifier.fillMaxWidth())
            }
        }, dismissButton = { TextButton(onClick = { showDeleteList = false }) { Text("ABBRECHEN") } }, confirmButton = {
            TextButton(onClick = { patterns = draft; showDeleteList = false }) { Text("ÜBERNEHMEN") }
        })
    }
}

@Composable
private fun EditorToggle(label: String, value: Boolean, enabled: Boolean = true, set: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(enabled = enabled) { set(!value) }, verticalAlignment = Alignment.CenterVertically) {
        Checkbox(value, set, enabled = enabled); Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun EditorChoice(label: String, value: String, choices: List<String>, set: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Box {
            TextButton(onClick = { expanded = true }) { Text(value) }
            DropdownMenu(expanded, { expanded = false }) {
                choices.forEach { choice -> DropdownMenuItem(text = { Text(choice) }, onClick = { set(choice); expanded = false }) }
            }
        }
    }
}
