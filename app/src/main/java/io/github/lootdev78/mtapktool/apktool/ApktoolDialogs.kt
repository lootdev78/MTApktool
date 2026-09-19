package io.github.lootdev78.mtapktool.apktool

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.apktool.android.runtime.ShellTokenizer
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

fun isApkLike(file: File): Boolean =
    file.isFile && file.extension.lowercase() in setOf("apk", "apks", "xapk", "apkm", "apkx")

fun isApktoolProject(file: File): Boolean = file.isDirectory && File(file, "apktool.yml").isFile

@Composable
fun ApkFileActionDialog(
    file: File,
    onDismiss: () -> Unit,
    onDecode: () -> Unit,
    onImportFramework: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(file.name, maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Card(
                    modifier = Modifier.fillMaxWidth().clickable(onClick = onDecode),
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text("Dekompilieren", fontWeight = FontWeight.SemiBold)
                        Text("APK mit Apktool in ein Projekt dekompilieren", style = MaterialTheme.typography.bodySmall)
                    }
                }
                Card(
                    modifier = Modifier.fillMaxWidth().clickable(onClick = onImportFramework),
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text("Als Framework importieren", fontWeight = FontWeight.SemiBold)
                        Text("Diese APK über den vorhandenen Apktool-Frameworkpfad installieren", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("ABBRECHEN") } },
    )
}

@Composable
fun ApktoolFrameworkImportDialog(
    file: File,
    onDismiss: () -> Unit,
    onJobQueued: (String) -> Unit = {},
) {
    val context = LocalContext.current
    var tag by remember(file) { mutableStateOf("") }
    val validTag = tag.isBlank() || tag.matches(Regex("[A-Za-z0-9._-]{1,80}"))

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Framework importieren") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(file.name, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    "Ziel: ${ApktoolSettings.frameworkDir()}",
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                )
                OutlinedTextField(
                    value = tag,
                    onValueChange = { tag = it.trim() },
                    label = { Text("Tag (optional)") },
                    supportingText = {
                        Text(if (validTag) "Leer = Standard-Framework" else "Erlaubt: A-Z, a-z, 0-9, Punkt, _ und -")
                    },
                    isError = !validTag,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("ABBRECHEN") } },
        confirmButton = {
            Button(
                enabled = file.isFile && file.extension.equals("apk", ignoreCase = true) && validTag,
                onClick = {
                    val command = buildString {
                        append("apktool install-framework -p ")
                        append(ShellTokenizer.quote(ApktoolSettings.frameworkDir()))
                        if (tag.isNotBlank()) {
                            append(" -t ").append(ShellTokenizer.quote(tag))
                        }
                        append(' ').append(ShellTokenizer.quote(file.absolutePath))
                    }
                    val jobId = ApktoolJobService.enqueue(
                        context = context,
                        title = "Framework ${file.name}",
                        command = command,
                    )
                    onJobQueued(jobId)
                    onDismiss()
                },
            ) { Text("IMPORTIEREN") }
        },
    )
}

@Composable
fun ApktoolDecodeDialog(file: File, onDismiss: () -> Unit, onJobQueued: (String) -> Unit = {}) {
    val context = LocalContext.current
    val defaults = remember(file) { ApktoolSettings.decodeDefaults(context) }
    val general = remember(file) { ApktoolSettings.generalDefaults(context) }
    val isSplitArchive = SplitArchiveSupport.isSplitArchive(file)
    val baseName = file.name.replace(Regex("(?i)\\.(apk|apks|xapk|apkm)$"), "")
    val root = if (general.decodeIntoOutputDirectory) ApktoolSettings.outputRoot(context) else ApktoolSettings.projectsRoot(context)

    var output by remember(file) { mutableStateOf(uniquePath(root, baseName)) }
    var framework by remember(file) { mutableStateOf(ApktoolSettings.frameworkTag(context)) }
    var resources by remember { mutableStateOf(!defaults.noResources) }
    var onlyManifest by remember { mutableStateOf(defaults.onlyManifest) }
    var classesDex by remember { mutableStateOf(!defaults.noSources) }
    var allDex by remember { mutableStateOf(defaults.allSources) }
    var createNomedia by remember { mutableStateOf(defaults.createNomedia) }
    var noDebug by remember { mutableStateOf(defaults.noDebugInfo) }
    var useRegisters by remember { mutableStateOf(defaults.useRegisters) }
    var preserveStructure by remember { mutableStateOf(defaults.preserveDirectoryStructure) }
    var keepBroken by remember { mutableStateOf(defaults.keepBrokenResources) }
    var removeSplit by remember { mutableStateOf(defaults.removeSplitTraces) }
    var removeProperty by remember { mutableStateOf(defaults.removePropertyTags) }
    var matchOriginal by remember { mutableStateOf(defaults.matchOriginal) }
    var force by remember { mutableStateOf(defaults.force) }
    var ignoreRaw by remember { mutableStateOf(defaults.ignoreRawValues) }
    var noAssets by remember { mutableStateOf(defaults.noAssets) }
    var resolveMode by remember { mutableStateOf(defaults.resourceResolveMode) }
    var verbose by remember { mutableStateOf(defaults.verbose) }
    var threads by remember { mutableIntStateOf(ApktoolSettings.apktoolThreads(context)) }
    var showSettings by remember { mutableStateOf(false) }
    var showThreads by remember { mutableStateOf(false) }

    var splitEntries by remember(file) { mutableStateOf<List<SplitArchiveSupport.ApkEntry>>(emptyList()) }
    var selectedSplitPath by remember(file) { mutableStateOf<String?>(null) }
    var splitScanning by remember(file) { mutableStateOf(isSplitArchive) }
    var splitScanError by remember(file) { mutableStateOf<String?>(null) }
    var allSplits by remember { mutableStateOf(false) }

    LaunchedEffect(file.absolutePath, isSplitArchive) {
        if (!isSplitArchive) return@LaunchedEffect
        splitScanning = true
        val result = withContext(Dispatchers.IO) { runCatching { SplitArchiveSupport.inspect(file) } }
        splitEntries = result.getOrDefault(emptyList())
        selectedSplitPath = splitEntries.firstOrNull { it.preferred }?.path ?: splitEntries.firstOrNull()?.path
        splitScanError = result.exceptionOrNull()?.message
            ?: if (splitEntries.isEmpty()) "Keine APK im Container gefunden" else null
        splitScanning = false
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(file.name, maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold) },
        text = {
            Column(modifier = Modifier.heightIn(max = 590.dp).verticalScroll(rememberScrollState())) {
                CheckRow("Ressourcen dekompilieren", resources) { resources = it; if (!it) onlyManifest = false }
                CheckRow("Klassen*.dex dekompilieren", classesDex) { classesDex = it; if (!it) allDex = false }
                CheckRow("Alle *.dex dekompilieren", allDex, enabled = classesDex) { allDex = it }
                CheckRow("die Datei \".nomedia\" erstellen", createNomedia) { createNomedia = it }

                SectionTitle("Rahmenwerk")
                ChoicePicker(
                    value = framework,
                    options = ApktoolSettings.availableFrameworkTags(context),
                    label = { ApktoolSettings.frameworkLabel(it) },
                ) { framework = it }

                if (isSplitArchive) {
                    SectionTitle("Dekompilierung zusätzlicher APKs")
                    CheckRow("Alle APK-Splits in eigene Projekte dekompilieren", allSplits) { allSplits = it }
                    when {
                        splitScanning -> Text("Container wird analysiert …", style = MaterialTheme.typography.bodySmall)
                        splitEntries.isEmpty() -> Text(splitScanError ?: "Keine APK gefunden", color = MaterialTheme.colorScheme.error)
                        !allSplits -> SplitEntryPicker(selectedSplitPath, splitEntries) { selectedSplitPath = it }
                    }
                }

                OutlinedTextField(
                    value = output,
                    onValueChange = { output = it },
                    label = { Text("Ausgabeordner") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { showSettings = true }) { Text("EINSTELLUNGEN") }
                TextButton(onClick = onDismiss) { Text("ABBRECHEN") }
            }
        },
        confirmButton = {
            TextButton(
                enabled = output.isNotBlank() && (!isSplitArchive || (!splitScanning && splitEntries.isNotEmpty())),
                onClick = {
                    val flags = mutableListOf<String>()
                    if (framework != "default") flags += listOf("-t", framework)
                    flags += listOf("-j", threads.coerceIn(1, 4).toString())
                    if (verbose) flags += "-v"
                    if (force) flags += "-f"
                    if (!classesDex) flags += "-s" else if (allDex) flags += "-a"
                    if (classesDex && noDebug) flags += "--no-debug-info"
                    if (classesDex && useRegisters) flags += "--use-registers"
                    if (!resources) flags += "-r" else if (onlyManifest) flags += "--only-manifest"
                    if (resources && !onlyManifest && resolveMode != "default") flags += listOf("--res-resolve-mode", resolveMode)
                    if (matchOriginal || preserveStructure) flags += "--match-original"
                    if (resources && !onlyManifest && keepBroken) flags += "--keep-broken-res"
                    if (resources && ignoreRaw) flags += "--ignore-raw-values"
                    if (noAssets) flags += "--no-assets"

                    val command = if (isSplitArchive) buildString {
                        append("apktool apks-decode ")
                        if (allSplits) append("--all-splits ")
                        else if (!selectedSplitPath.isNullOrBlank()) append("--entry ").append(ShellTokenizer.quote(selectedSplitPath!!)).append(' ')
                        append(ShellTokenizer.quote(file.absolutePath)).append(' ')
                        append(ShellTokenizer.quote(output)).append(" --")
                        flags.forEach { append(' ').append(ShellTokenizer.quote(it)) }
                    } else buildString {
                        append("apktool decode")
                        flags.forEach { append(' ').append(ShellTokenizer.quote(it)) }
                        append(" -o ").append(ShellTokenizer.quote(output)).append(' ').append(ShellTokenizer.quote(file.absolutePath))
                    }

                    val jobId = ApktoolJobService.enqueue(
                        context = context,
                        title = "Decode ${file.name}",
                        command = command,
                        postDecodeRoot = output,
                        createNomedia = createNomedia,
                        removeSplitTraces = removeSplit,
                        removePropertyTags = removeProperty,
                    )
                    onJobQueued(jobId)
                    onDismiss()
                },
            ) { Text("OK") }
        },
    )

    if (showSettings) {
        AlertDialog(
            onDismissRequest = { showSettings = false },
            title = { Text("Einstellungen") },
            text = {
                Column(modifier = Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState())) {
                    CheckRow("Debug-Informationen schreiben", !noDebug) { noDebug = !it }
                    CheckRow("Nur AndroidManifest.xml dekompilieren", onlyManifest, enabled = resources) { onlyManifest = it }
                    CheckRow("Verwenden Sie \"Register\" statt \"Lokale\".", useRegisters) { useRegisters = it }
                    CheckRow("Beibehaltung der Ordnerstruktur", preserveStructure) { preserveStructure = it }
                    if (resources && !onlyManifest) {
                        Text("Ressourcen-Auflösung", style = MaterialTheme.typography.labelLarge)
                        ChoicePicker(resolveMode, ApktoolSettings.resourceResolveModes, { mode ->
                            when (mode) {
                                "greedy" -> "Greedy"
                                "lazy" -> "Lazy"
                                else -> "Standard"
                            }
                        }) { resolveMode = it }
                    }
                    CheckRow("Rohwerte in XML ignorieren", ignoreRaw, enabled = resources) { ignoreRaw = it }
                    CheckRow("Assets nicht dekompilieren", noAssets) { noAssets = it }
                    CheckRow("Gebrochene Ressourcen beibehalten", keepBroken) { keepBroken = it }
                    CheckRow("Gespaltene Spuren entfernen", removeSplit) { removeSplit = it }
                    CheckRow("<Eigenschaft> entfernen", removeProperty) { removeProperty = it }
                    CheckRow("Original anpassen", matchOriginal) { matchOriginal = it }
                    CheckRow("Vorhandenes Projekt überschreiben", force) { force = it }
                    CheckRow("Ausführlich", verbose) { verbose = it }
                }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { showThreads = true }) { Text("THREADS") }
                    TextButton(onClick = { showSettings = false }) { Text("ABBRECHEN") }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    ApktoolSettings.saveDecodeDefaults(
                        context,
                        ApktoolDecodeDefaults(
                            force = force, allSources = allDex, noSources = !classesDex, noDebugInfo = noDebug,
                            noResources = !resources, onlyManifest = onlyManifest, matchOriginal = matchOriginal,
                            preserveDirectoryStructure = preserveStructure, keepBrokenResources = keepBroken,
                            ignoreRawValues = ignoreRaw, noAssets = noAssets, resourceResolveMode = resolveMode,
                            useRegisters = useRegisters, createNomedia = createNomedia, removeSplitTraces = removeSplit,
                            removePropertyTags = removeProperty, verbose = verbose,
                        ),
                    )
                    ApktoolSettings.setApktoolThreads(context, threads)
                    showSettings = false
                }) { Text("SPEICHERN") }
            },
        )
    }

    if (showThreads) {
        ThreadPickerDialog("Dekompilierung smali", threads, onSave = {
            threads = it; ApktoolSettings.setApktoolThreads(context, it); showThreads = false
        }, onDismiss = { showThreads = false })
    }
}

@Composable
fun ApktoolBuildDialog(project: File, onDismiss: () -> Unit, onJobQueued: (String) -> Unit = {}) {
    val context = LocalContext.current
    val defaults = remember(project) { ApktoolSettings.buildDefaults(context) }
    val general = remember(project) { ApktoolSettings.generalDefaults(context) }

    var aapt by remember { mutableStateOf(ApktoolSettings.aaptVariant(context)) }
    var customAapt by remember { mutableStateOf(ApktoolSettings.customAapt2Path(context)) }
    var framework by remember { mutableStateOf(ApktoolSettings.frameworkTag(context)) }
    var signature by remember { mutableStateOf(ApktoolSettings.signatureDefaults(context)) }
    var threads by remember { mutableIntStateOf(ApktoolSettings.apktoolThreads(context)) }
    var force by remember { mutableStateOf(defaults.force) }
    var debuggable by remember { mutableStateOf(defaults.debuggable) }
    var copyOriginal by remember { mutableStateOf(defaults.copyOriginal) }
    var noCrunch by remember { mutableStateOf(defaults.noCrunch) }
    var netSec by remember { mutableStateOf(defaults.networkSecurityConfig) }
    var netSecKeep by remember { mutableStateOf(defaults.networkSecurityKeepExisting) }
    var align by remember { mutableStateOf(defaults.zipalign) }
    var sign by remember { mutableStateOf(defaults.sign) }
    var deleteBuild by remember { mutableStateOf(defaults.deleteBuildDirectory) }
    var verbose by remember { mutableStateOf(defaults.verbose) }
    var showSettings by remember { mutableStateOf(false) }
    var showThreads by remember { mutableStateOf(false) }
    var showSignature by remember { mutableStateOf(false) }

    val suffix = general.apkSuffix
    val outputRoot = if (general.buildIntoOutputDirectory) ApktoolSettings.outputRoot(context) else File(project, "dist").absolutePath
    var output by remember(project, suffix, outputRoot) { mutableStateOf(uniqueFilePath(outputRoot, project.name + suffix + ".apk")) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Projekt kompilieren \"${project.name}\"?") },
        text = {
            Column(modifier = Modifier.heightIn(max = 590.dp).verticalScroll(rememberScrollState())) {
                SectionTitle("AAPT2")
                Text(
                    "AAPT2 ist immer aktiv. Wähle Automatisch für die empfohlene Gerätevariante.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                ChoicePicker(aapt, ApktoolSettings.aaptOptions, { ApktoolSettings.aaptLabel(it) }) { aapt = it }
                if (aapt == "custom") {
                    OutlinedTextField(customAapt, { customAapt = it }, label = { Text("Custom AAPT2") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }

                SectionTitle("Rahmenwerk")
                ChoicePicker(framework, ApktoolSettings.availableFrameworkTags(context), { ApktoolSettings.frameworkLabel(it) }) { framework = it }

                SectionTitle("Wähle eine Signaturdatei aus")
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text(ApktoolSettings.signatureLabel(signature), modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    IconButton(onClick = { showSignature = true }) { Icon(Icons.Default.Settings, contentDescription = "Signatur") }
                }

                OutlinedTextField(output, { output = it }, label = { Text("Ausgabe-APK") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { showSettings = true }) { Text("EINSTELLUNGEN") }
                TextButton(onClick = onDismiss) { Text("ABBRECHEN") }
            }
        },
        confirmButton = {
            TextButton(
                enabled = output.isNotBlank() && (aapt != "custom" || customAapt.isNotBlank()) && (!sign || signature.profile != "custom" || signature.customKeystorePath.isNotBlank()),
                onClick = {
                    val command = buildString {
                        append("apktool build -j ").append(threads.coerceIn(1, 4))
                        if (verbose) append(" -v")
                        if (framework != "default") append(" -t ").append(ShellTokenizer.quote(framework))
                        if (aapt == "custom") append(" --aapt ").append(ShellTokenizer.quote(customAapt.trim()))
                        else append(" --aapt-variant ").append(ShellTokenizer.quote(aapt))
                        if (force) append(" -f")
                        if (debuggable) append(" --debuggable")
                        if (copyOriginal) append(" --copy-original")
                        if (noCrunch) append(" --no-crunch")
                        if (netSec) {
                            append(" --net-sec-conf")
                            if (netSecKeep) append(" --net-sec-conf-keep-existing")
                        }
                        append(" -o ").append(ShellTokenizer.quote(output)).append(' ').append(ShellTokenizer.quote(project.absolutePath))
                    }
                    val jobId = ApktoolJobService.enqueue(
                        context = context,
                        title = "Build ${project.name}",
                        command = command,
                        postAlign = align,
                        postSign = sign,
                        signature = signature,
                        cleanBuildProject = if (deleteBuild) project.absolutePath else null,
                    )
                    onJobQueued(jobId)
                    onDismiss()
                },
            ) { Text("OK") }
        },
    )

    if (showSettings) {
        AlertDialog(
            onDismissRequest = { showSettings = false },
            title = { Text("Einstellungen") },
            text = {
                Column(modifier = Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState())) {
                    CheckRow("Vollständigen Build erzwingen", force) { force = it }
                    CheckRow("apk als debuggingfähig einstellen", debuggable) { debuggable = it }
                    CheckRow("Resource-Crunching deaktivieren", noCrunch) { noCrunch = it }
                    CheckRow("Netzwerksicherheitskonfiguration hinzufügen", netSec) { netSec = it }
                    CheckRow("Nicht ändern, wenn sie vorhanden ist", netSecKeep, enabled = netSec) { netSecKeep = it }
                    CheckRow("Ordner \"build\" löschen", deleteBuild) { deleteBuild = it }
                    CheckRow("Ersetzen von Prüfsummen aus dem Original", copyOriginal) { copyOriginal = it }
                    CheckRow("Zipalign", align) { align = it }
                    CheckRow("Signieren", sign) { sign = it }
                    CheckRow("Ausführlich", verbose) { verbose = it }
                }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { showThreads = true }) { Text("THREADS") }
                    TextButton(onClick = { showSettings = false }) { Text("ABBRECHEN") }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    ApktoolSettings.saveBuildDefaults(
                        context,
                        ApktoolBuildDefaults(force, debuggable, copyOriginal, noCrunch, netSec, netSecKeep, align, sign, deleteBuild, verbose),
                    )
                    ApktoolSettings.setApktoolThreads(context, threads)
                    showSettings = false
                }) { Text("SPEICHERN") }
            },
        )
    }

    if (showThreads) {
        ThreadPickerDialog("Kompilierung smali", threads, onSave = {
            threads = it; ApktoolSettings.setApktoolThreads(context, it); showThreads = false
        }, onDismiss = { showThreads = false })
    }

    if (showSignature) {
        SignatureManagerDialog(onBack = {
            signature = ApktoolSettings.signatureDefaults(context)
            showSignature = false
        })
    }
}

@Composable
private fun ThreadPickerDialog(title: String, value: Int, onSave: (Int) -> Unit, onDismiss: () -> Unit) {
    var selected by remember(value) { mutableIntStateOf(value.coerceIn(1, 4)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                (1..4).forEach { n ->
                    TextButton(onClick = { selected = n }, modifier = Modifier.fillMaxWidth()) {
                        Text("$n", color = if (selected == n) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("ABBRECHEN") } },
        confirmButton = { TextButton(onClick = { onSave(selected) }) { Text("SPEICHERN") } },
    )
}

@Composable
fun ApktoolJobsDialog(jobs: List<ApktoolJobInfo>, onCancel: (String) -> Unit, onCancelAll: () -> Unit, onDismiss: () -> Unit) {
    val active = jobs.count { !it.isTerminal }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Apktool Jobs${if (active > 0) " ($active aktiv)" else ""}") },
        text = {
            if (jobs.isEmpty()) Text("Keine Jobs in dieser App-Sitzung.")
            else LazyColumn(modifier = Modifier.heightIn(max = 520.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(jobs, key = { it.id }) { job ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(job.title, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(job.status, style = MaterialTheme.typography.labelSmall)
                            }
                            Text(job.line.lineSequence().lastOrNull().orEmpty(), style = MaterialTheme.typography.bodySmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
                            if (!job.isTerminal) TextButton(onClick = { onCancel(job.id) }, modifier = Modifier.align(Alignment.End)) { Text("STOP") }
                            else if (!job.output.isNullOrBlank()) Text(job.output, style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace)
                        }
                    }
                }
            }
        },
        dismissButton = { if (active > 0) TextButton(onClick = onCancelAll) { Text("ALLE STOPPEN") } },
        confirmButton = { TextButton(onClick = onDismiss) { Text("SCHLIESSEN") } },
    )
}

@Composable
fun ApktoolCliDialog(onDismiss: () -> Unit, onJobQueued: (String) -> Unit = {}) {
    val context = LocalContext.current
    var command by remember { mutableStateOf("apktool --help") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Apktool CLI") },
        text = {
            OutlinedTextField(
                value = command,
                onValueChange = { command = it },
                minLines = 4,
                maxLines = 10,
                label = { Text("Command") },
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                modifier = Modifier.fillMaxWidth(),
            )
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("ABBRECHEN") } },
        confirmButton = {
            Button(enabled = command.isNotBlank(), onClick = { val jobId = ApktoolJobService.enqueue(context, "CLI", command); onJobQueued(jobId); onDismiss() }) { Text("START") }
        },
    )
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp, bottom = 2.dp))
}

@Composable
private fun CheckRow(label: String, checked: Boolean, enabled: Boolean = true, onChecked: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clickable(enabled = enabled) { onChecked(!checked) },
    ) {
        Checkbox(checked = checked, onCheckedChange = onChecked, enabled = enabled)
        Text(label, color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.45f))
    }
}

@Composable
private fun ChoicePicker(value: String, options: List<String>, label: (String) -> String, onSelected: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = Modifier.fillMaxWidth()) {
        TextButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(label(value), modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.width(8.dp)); Text("▾")
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.distinct().forEach { option ->
                DropdownMenuItem(text = { Text(label(option)) }, onClick = { expanded = false; onSelected(option) })
            }
        }
    }
}

@Composable
private fun SplitEntryPicker(value: String?, entries: List<SplitArchiveSupport.ApkEntry>, onSelected: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val selected = entries.firstOrNull { it.path == value } ?: entries.firstOrNull()
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.weight(1f)) {
            Text("APK im Container", style = MaterialTheme.typography.labelLarge)
            Text(selected?.path ?: "Automatisch", style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Box {
            TextButton(onClick = { expanded = true }) { Text(if (selected?.preferred == true) "Base (auto)" else "Auswählen") }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                entries.forEach { entry ->
                    DropdownMenuItem(
                        text = { Text((if (entry.preferred) "Auto • " else "") + entry.path, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        onClick = { onSelected(entry.path); expanded = false },
                    )
                }
            }
        }
    }
}

private fun uniquePath(root: String, base: String): String {
    val dir = File(root); val first = File(dir, base); if (!first.exists()) return first.absolutePath
    var i = 1; while (true) { val c = File(dir, "$base-$i"); if (!c.exists()) return c.absolutePath; i++ }
}

private fun uniqueFilePath(root: String, name: String): String {
    val dir = File(root); val first = File(dir, name); if (!first.exists()) return first.absolutePath
    val dot = name.lastIndexOf('.'); val base = if (dot > 0) name.substring(0, dot) else name; val ext = if (dot > 0) name.substring(dot) else ""
    var i = 1; while (true) { val c = File(dir, "$base-$i$ext"); if (!c.exists()) return c.absolutePath; i++ }
}
