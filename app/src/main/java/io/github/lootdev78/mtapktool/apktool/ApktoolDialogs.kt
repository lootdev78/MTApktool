package io.github.lootdev78.mtapktool.apktool

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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

fun isApkLike(file: File): Boolean = file.isFile && file.extension.lowercase() in setOf("apk", "apks", "xapk", "apkm")
fun isApktoolProject(file: File): Boolean = file.isDirectory && File(file, "apktool.yml").isFile

@Composable
fun ApktoolDecodeDialog(file: File, onDismiss: () -> Unit, onOpenSettings: () -> Unit) {
    val context = LocalContext.current
    val defaults = remember(file) { ApktoolSettings.decodeDefaults(context) }
    val general = remember(file) { ApktoolSettings.generalDefaults(context) }
    val isSplitArchive = SplitArchiveSupport.isSplitArchive(file)
    val baseName = file.name.replace(Regex("(?i)\\.(apk|apks|xapk|apkm)$"), "")
    val decodeRoot = if (general.decodeIntoOutputDirectory) ApktoolSettings.outputRoot(context) else ApktoolSettings.projectsRoot(context)

    var output by remember(file) { mutableStateOf(uniquePath(decodeRoot, baseName)) }
    var framework by remember(file) { mutableStateOf(ApktoolSettings.frameworkTag(context)) }
    var resources by remember { mutableStateOf(!defaults.noResources) }
    var onlyManifest by remember { mutableStateOf(defaults.onlyManifest) }
    var classesDex by remember { mutableStateOf(!defaults.noSources) }
    var allDex by remember { mutableStateOf(defaults.allSources) }
    var noDebug by remember { mutableStateOf(defaults.noDebugInfo) }
    var useRegisters by remember { mutableStateOf(defaults.useRegisters) }
    var createNomedia by remember { mutableStateOf(defaults.createNomedia) }
    var force by remember { mutableStateOf(defaults.force) }
    var matchOriginal by remember { mutableStateOf(defaults.matchOriginal) }
    var keepBroken by remember { mutableStateOf(defaults.keepBrokenResources) }
    var ignoreRaw by remember { mutableStateOf(defaults.ignoreRawValues) }
    var noAssets by remember { mutableStateOf(defaults.noAssets) }
    var resolveMode by remember { mutableStateOf(defaults.resourceResolveMode) }
    var removeSplit by remember { mutableStateOf(defaults.removeSplitTraces) }
    var removeProperty by remember { mutableStateOf(defaults.removePropertyTags) }
    var verbose by remember { mutableStateOf(defaults.verbose) }
    var threads by remember { mutableIntStateOf(ApktoolSettings.apktoolThreads(context)) }
    var showSettings by remember { mutableStateOf(false) }
    var showThreads by remember { mutableStateOf(false) }

    var allSplits by remember { mutableStateOf(false) }
    var splitEntries by remember(file) { mutableStateOf<List<SplitArchiveSupport.ApkEntry>>(emptyList()) }
    var selectedSplitPath by remember(file) { mutableStateOf<String?>(null) }
    var splitScanning by remember(file) { mutableStateOf(isSplitArchive) }
    var splitScanError by remember(file) { mutableStateOf<String?>(null) }

    LaunchedEffect(file.absolutePath, isSplitArchive) {
        if (!isSplitArchive) return@LaunchedEffect
        splitScanning = true
        val result = withContext(Dispatchers.IO) { runCatching { SplitArchiveSupport.inspect(file) } }
        splitEntries = result.getOrDefault(emptyList())
        selectedSplitPath = splitEntries.firstOrNull { it.preferred }?.path ?: splitEntries.firstOrNull()?.path
        splitScanError = result.exceptionOrNull()?.message ?: if (splitEntries.isEmpty()) "Keine APK im Container gefunden" else null
        splitScanning = false
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(file.name, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            Column(modifier = Modifier.heightIn(max = 590.dp).verticalScroll(rememberScrollState())) {
                CheckRow("Ressourcen dekompilieren", resources) {
                    resources = it
                    if (!it) onlyManifest = false
                }
                CheckRow("Klassen*.dex dekompilieren", classesDex) {
                    classesDex = it
                    if (!it) allDex = false
                }
                CheckRow("Alle *.dex dekompilieren", allDex, enabled = classesDex) { allDex = it }
                CheckRow("die Datei \".nomedia\" erstellen", createNomedia) { createNomedia = it }

                Text("Rahmenwerk", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                ChoicePicker(
                    label = "",
                    value = framework,
                    options = ApktoolSettings.frameworkOptions,
                    valueLabel = { ApktoolSettings.frameworkLabel(it) },
                    onSelected = { framework = it },
                )

                if (isSplitArchive) {
                    Text("Dekompilierung zusätzlicher APKs", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                    CheckRow("Alle APK-Splits dekompilieren", allSplits) { allSplits = it }
                    if (!allSplits) {
                        when {
                            splitScanning -> Text("Container wird analysiert …", style = MaterialTheme.typography.bodySmall)
                            splitEntries.isNotEmpty() -> SplitEntryPicker(selectedSplitPath, splitEntries) { selectedSplitPath = it }
                            else -> Text(splitScanError ?: "Keine APK gefunden", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }

                OutlinedTextField(
                    value = output,
                    onValueChange = { output = it },
                    label = { Text("Ausgabeordner") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                Text(
                    "Threads: $threads • ${if (resources) if (onlyManifest) "nur Manifest" else "Ressourcen" else "keine Ressourcen"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
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
            Button(
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
                    if (matchOriginal) flags += "--match-original"
                    if (resources && !onlyManifest && keepBroken) flags += "--keep-broken-res"
                    if (resources && ignoreRaw) flags += "--ignore-raw-values"
                    if (noAssets) flags += "--no-assets"

                    val command = if (isSplitArchive) {
                        buildString {
                            append("apktool apks-decode ")
                            if (allSplits) append("--all-splits ")
                            else if (!selectedSplitPath.isNullOrBlank()) append("--entry ").append(ShellTokenizer.quote(selectedSplitPath!!)).append(' ')
                            append(ShellTokenizer.quote(file.absolutePath)).append(' ')
                            append(ShellTokenizer.quote(output)).append(" --")
                            flags.forEach { append(' ').append(ShellTokenizer.quote(it)) }
                        }
                    } else {
                        buildString {
                            append("apktool decode")
                            flags.forEach { append(' ').append(ShellTokenizer.quote(it)) }
                            append(" -o ").append(ShellTokenizer.quote(output))
                            append(' ').append(ShellTokenizer.quote(file.absolutePath))
                        }
                    }
                    ApktoolJobService.enqueue(
                        context = context,
                        title = "Decode ${file.name}",
                        command = command,
                        postDecodeRoot = output,
                        createNomedia = createNomedia,
                        removeSplitTraces = removeSplit,
                        removePropertyTags = removeProperty,
                    )
                    onDismiss()
                },
            ) { Text("OK") }
        },
    )

    if (showSettings) {
        DecodeQuickSettingsDialog(
            noDebug = noDebug,
            useRegisters = useRegisters,
            matchOriginal = matchOriginal,
            keepBroken = keepBroken,
            removeSplit = removeSplit,
            removeProperty = removeProperty,
            force = force,
            verbose = verbose,
            ignoreRaw = ignoreRaw,
            noAssets = noAssets,
            onlyManifest = onlyManifest,
            resolveMode = resolveMode,
            onNoDebug = { noDebug = it },
            onUseRegisters = { useRegisters = it },
            onMatchOriginal = { matchOriginal = it },
            onKeepBroken = { keepBroken = it },
            onRemoveSplit = { removeSplit = it },
            onRemoveProperty = { removeProperty = it },
            onForce = { force = it },
            onVerbose = { verbose = it },
            onIgnoreRaw = { ignoreRaw = it },
            onNoAssets = { noAssets = it },
            onOnlyManifest = { onlyManifest = it },
            onResolveMode = { resolveMode = it },
            onThreads = { showThreads = true },
            onGlobalSettings = onOpenSettings,
            onDismiss = { showSettings = false },
            onSave = {
                ApktoolSettings.saveDecodeDefaults(
                    context,
                    ApktoolDecodeDefaults(
                        force = force,
                        allSources = allDex,
                        noSources = !classesDex,
                        noDebugInfo = noDebug,
                        noResources = !resources,
                        onlyManifest = onlyManifest,
                        matchOriginal = matchOriginal,
                        keepBrokenResources = keepBroken,
                        ignoreRawValues = ignoreRaw,
                        noAssets = noAssets,
                        resourceResolveMode = resolveMode,
                        useRegisters = useRegisters,
                        createNomedia = createNomedia,
                        removeSplitTraces = removeSplit,
                        removePropertyTags = removeProperty,
                        verbose = verbose,
                    ),
                )
                ApktoolSettings.setApktoolThreads(context, threads)
                showSettings = false
            },
        )
    }
    if (showThreads) {
        ThreadPickerDialog("Dekompilierung smali", threads, { threads = it; ApktoolSettings.setApktoolThreads(context, it); showThreads = false }, { showThreads = false })
    }
}

@Composable
private fun DecodeQuickSettingsDialog(
    noDebug: Boolean,
    useRegisters: Boolean,
    matchOriginal: Boolean,
    keepBroken: Boolean,
    removeSplit: Boolean,
    removeProperty: Boolean,
    force: Boolean,
    verbose: Boolean,
    ignoreRaw: Boolean,
    noAssets: Boolean,
    onlyManifest: Boolean,
    resolveMode: String,
    onNoDebug: (Boolean) -> Unit,
    onUseRegisters: (Boolean) -> Unit,
    onMatchOriginal: (Boolean) -> Unit,
    onKeepBroken: (Boolean) -> Unit,
    onRemoveSplit: (Boolean) -> Unit,
    onRemoveProperty: (Boolean) -> Unit,
    onForce: (Boolean) -> Unit,
    onVerbose: (Boolean) -> Unit,
    onIgnoreRaw: (Boolean) -> Unit,
    onNoAssets: (Boolean) -> Unit,
    onOnlyManifest: (Boolean) -> Unit,
    onResolveMode: (String) -> Unit,
    onThreads: () -> Unit,
    onGlobalSettings: () -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Einstellungen") },
        text = {
            Column(modifier = Modifier.heightIn(max = 570.dp).verticalScroll(rememberScrollState())) {
                CheckRow("Debug-Informationen schreiben", !noDebug) { onNoDebug(!it) }
                CheckRow("Register statt Lokale verwenden", useRegisters, onChecked = onUseRegisters)
                CheckRow("Beibehaltung der Ordnerstruktur", matchOriginal, onChecked = onMatchOriginal)
                CheckRow("Hinzufügen \"APKTOOL_DUMMY\"", true, enabled = false) { }
                Text("Apktool 3.x ergänzt fehlende Ressourcenreferenzen automatisch als APKTOOL_DUMMY.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 48.dp, bottom = 4.dp))
                CheckRow("Gebrochene Ressourcen beibehalten", keepBroken, onChecked = onKeepBroken)
                CheckRow("Gespaltene Spuren entfernen", removeSplit, onChecked = onRemoveSplit)
                CheckRow("<property> entfernen", removeProperty, onChecked = onRemoveProperty)
                CheckRow("Original anpassen", matchOriginal, onChecked = onMatchOriginal)
                CheckRow("Nur Manifest", onlyManifest, onChecked = onOnlyManifest)
                CheckRow("Raw values ignorieren", ignoreRaw, onChecked = onIgnoreRaw)
                CheckRow("Assets nicht dekompilieren", noAssets, onChecked = onNoAssets)
                CheckRow("Vorhandenes Projekt überschreiben", force, onChecked = onForce)
                CheckRow("Ausführlich", verbose, onChecked = onVerbose)
                ChoicePicker("Resource resolve", resolveMode, ApktoolSettings.resourceResolveModes, { it }, onSelected = onResolveMode)
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onThreads) { Text("THREADS") }
                TextButton(onClick = onGlobalSettings) { Text("MEHR") }
                TextButton(onClick = onDismiss) { Text("ABBRECHEN") }
            }
        },
        confirmButton = { TextButton(onClick = onSave) { Text("SPEICHERN") } },
    )
}

@Composable
fun ApktoolBuildDialog(project: File, onDismiss: () -> Unit, onOpenSettings: () -> Unit) {
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
    var align by remember { mutableStateOf(defaults.zipalign) }
    var sign by remember { mutableStateOf(defaults.sign) }
    var deleteBuild by remember { mutableStateOf(defaults.deleteBuildDirectory) }
    var verbose by remember { mutableStateOf(defaults.verbose) }
    var showSettings by remember { mutableStateOf(false) }
    var showThreads by remember { mutableStateOf(false) }
    var showSignature by remember { mutableStateOf(false) }

    val suffix = general.apkSuffix
    val outputRoot = if (general.buildIntoOutputDirectory) ApktoolSettings.outputRoot(context) else File(project, "dist").absolutePath
    var output by remember(project, suffix, outputRoot) {
        mutableStateOf(uniqueFilePath(outputRoot, project.name + suffix + ".apk"))
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Projekt kompilieren \"${project.name}\"?") },
        text = {
            Column(modifier = Modifier.heightIn(max = 590.dp).verticalScroll(rememberScrollState())) {
                Text("AAPT2", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                ChoicePicker("", aapt, ApktoolSettings.aaptOptions, { ApktoolSettings.aaptLabel(it) }) { aapt = it }
                if (aapt == "custom") {
                    OutlinedTextField(customAapt, { customAapt = it }, label = { Text("Custom AAPT2") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
                Text("Rahmenwerk", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 5.dp))
                ChoicePicker("", framework, ApktoolSettings.frameworkOptions, { ApktoolSettings.frameworkLabel(it) }) { framework = it }

                Text("Wähle eine Signaturdatei aus", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 5.dp))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text(ApktoolSettings.signatureLabel(signature), modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    IconButton(onClick = { showSignature = true }) { Icon(Icons.Default.Settings, contentDescription = "Signatur einstellen") }
                }

                OutlinedTextField(
                    value = output,
                    onValueChange = { output = it },
                    label = { Text("Ausgabe-APK") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                )
                Text("Threads: $threads • ${if (align) "zipalign" else "kein zipalign"} • ${if (sign) "signieren" else "nicht signieren"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { showSettings = true }) { Text("EINSTELLUNGEN") }
                TextButton(onClick = onDismiss) { Text("ABBRECHEN") }
            }
        },
        confirmButton = {
            Button(
                enabled = output.isNotBlank() && (aapt != "custom" || customAapt.isNotBlank()) && (!sign || signature.profile != "custom" || signature.customKeystorePath.isNotBlank()),
                onClick = {
                    val command = buildString {
                        append("apktool build")
                        append(" -j ").append(threads.coerceIn(1, 4))
                        if (verbose) append(" -v")
                        if (framework != "default") append(" -t ").append(ShellTokenizer.quote(framework))
                        if (aapt == "custom") append(" --aapt ").append(ShellTokenizer.quote(customAapt.trim()))
                        else append(" --aapt-variant ").append(ShellTokenizer.quote(aapt))
                        if (force) append(" -f")
                        if (debuggable) append(" --debuggable")
                        if (copyOriginal) append(" --copy-original")
                        if (noCrunch) append(" --no-crunch")
                        if (netSec) append(" --net-sec-conf")
                        append(" -o ").append(ShellTokenizer.quote(output))
                        append(' ').append(ShellTokenizer.quote(project.absolutePath))
                    }
                    ApktoolJobService.enqueue(
                        context = context,
                        title = "Build ${project.name}",
                        command = command,
                        postAlign = align,
                        postSign = sign,
                        signature = signature,
                        cleanBuildProject = if (deleteBuild) project.absolutePath else null,
                    )
                    onDismiss()
                },
            ) { Text("OK") }
        },
    )

    if (showSettings) {
        BuildQuickSettingsDialog(
            debuggable = debuggable,
            netSec = netSec,
            deleteBuild = deleteBuild,
            copyOriginal = copyOriginal,
            force = force,
            noCrunch = noCrunch,
            verbose = verbose,
            align = align,
            sign = sign,
            onDebuggable = { debuggable = it },
            onNetSec = { netSec = it },
            onDeleteBuild = { deleteBuild = it },
            onCopyOriginal = { copyOriginal = it },
            onForce = { force = it },
            onNoCrunch = { noCrunch = it },
            onVerbose = { verbose = it },
            onAlign = { align = it },
            onSign = { sign = it },
            onThreads = { showThreads = true },
            onGlobalSettings = onOpenSettings,
            onDismiss = { showSettings = false },
            onSave = {
                ApktoolSettings.saveBuildDefaults(context, ApktoolBuildDefaults(force, debuggable, copyOriginal, noCrunch, netSec, align, sign, deleteBuild, verbose))
                ApktoolSettings.setApktoolThreads(context, threads)
                showSettings = false
            },
        )
    }
    if (showThreads) {
        ThreadPickerDialog("Kompilierung smali", threads, { threads = it; ApktoolSettings.setApktoolThreads(context, it); showThreads = false }, { showThreads = false })
    }
    if (showSignature) {
        SignatureManagerDialog(onBack = {
            signature = ApktoolSettings.signatureDefaults(context)
            showSignature = false
        })
    }
}

@Composable
private fun BuildQuickSettingsDialog(
    debuggable: Boolean,
    netSec: Boolean,
    deleteBuild: Boolean,
    copyOriginal: Boolean,
    force: Boolean,
    noCrunch: Boolean,
    verbose: Boolean,
    align: Boolean,
    sign: Boolean,
    onDebuggable: (Boolean) -> Unit,
    onNetSec: (Boolean) -> Unit,
    onDeleteBuild: (Boolean) -> Unit,
    onCopyOriginal: (Boolean) -> Unit,
    onForce: (Boolean) -> Unit,
    onNoCrunch: (Boolean) -> Unit,
    onVerbose: (Boolean) -> Unit,
    onAlign: (Boolean) -> Unit,
    onSign: (Boolean) -> Unit,
    onThreads: () -> Unit,
    onGlobalSettings: () -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Einstellungen") },
        text = {
            Column(modifier = Modifier.heightIn(max = 570.dp).verticalScroll(rememberScrollState())) {
                CheckRow("APK als debuggingfähig einstellen", debuggable, onChecked = onDebuggable)
                CheckRow("Netzwerksicherheitskonfiguration hinzufügen", netSec, onChecked = onNetSec)
                CheckRow("Ordner \"build\" löschen", deleteBuild, onChecked = onDeleteBuild)
                CheckRow("Originaldateien/Prüfsummen übernehmen", copyOriginal, onChecked = onCopyOriginal)
                CheckRow("Force build", force, onChecked = onForce)
                CheckRow("No crunch", noCrunch, onChecked = onNoCrunch)
                CheckRow("Zipalign", align, onChecked = onAlign)
                CheckRow("Signieren", sign, onChecked = onSign)
                CheckRow("Ausführlich", verbose, onChecked = onVerbose)
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onThreads) { Text("THREADS") }
                TextButton(onClick = onGlobalSettings) { Text("MEHR") }
                TextButton(onClick = onDismiss) { Text("ABBRECHEN") }
            }
        },
        confirmButton = { TextButton(onClick = onSave) { Text("SPEICHERN") } },
    )
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
                    val selectedNow = selected == n
                    TextButton(onClick = { selected = n }, modifier = Modifier.fillMaxWidth()) {
                        Text(if (selectedNow) "› $n ‹" else "$n", fontWeight = if (selectedNow) FontWeight.Bold else FontWeight.Normal)
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
                            Text(job.line.lineSequence().lastOrNull().orEmpty(), style = MaterialTheme.typography.bodySmall, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
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
fun ApktoolCliDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var command by remember { mutableStateOf("apktool --help") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Apktool CLI") },
        text = {
            Column {
                OutlinedTextField(
                    value = command,
                    onValueChange = { command = it },
                    minLines = 4,
                    maxLines = 10,
                    label = { Text("Command") },
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("decode/build/framework/zipalign/apksigner, --use-registers und apktool-original werden unterstützt.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("ABBRECHEN") } },
        confirmButton = {
            Button(enabled = command.isNotBlank(), onClick = {
                ApktoolJobService.enqueue(context, "CLI", command)
                onDismiss()
            }) { Text("START") }
        },
    )
}

@Composable
private fun CheckRow(label: String, checked: Boolean, enabled: Boolean = true, onChecked: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Checkbox(checked = checked, onCheckedChange = onChecked, enabled = enabled)
        Text(label, color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.4f))
    }
}

@Composable
private fun SplitEntryPicker(value: String?, entries: List<SplitArchiveSupport.ApkEntry>, onSelected: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val selected = entries.firstOrNull { it.path == value } ?: entries.firstOrNull()
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.weight(1f)) {
            Text("APK im Container", style = MaterialTheme.typography.labelLarge)
            Text(selected?.path ?: "Automatisch", style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Box {
            TextButton(onClick = { expanded = true }) { Text(if (selected?.preferred == true) "Base (auto)" else "Auswählen") }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                entries.forEach { entry ->
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(entry.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    buildString {
                                        if (entry.preferred) append("Auto • ")
                                        append(entry.path)
                                        if (entry.size > 0) append(" • %.1f MiB".format(entry.sizeMiB))
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        },
                        onClick = { onSelected(entry.path); expanded = false },
                    )
                }
            }
        }
    }
}

@Composable
private fun ChoicePicker(
    label: String,
    value: String,
    options: List<String>,
    valueLabel: (String) -> String,
    enabled: Boolean = true,
    onSelected: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        if (label.isNotBlank()) Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
        else Spacer(Modifier.weight(1f))
        Box {
            TextButton(onClick = { expanded = true }, enabled = enabled) { Text(valueLabel(value)) }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { option ->
                    DropdownMenuItem(text = { Text(valueLabel(option)) }, onClick = { expanded = false; onSelected(option) })
                }
            }
        }
    }
}

private fun uniquePath(root: String, base: String): String {
    val dir = File(root)
    val first = File(dir, base)
    if (!first.exists()) return first.absolutePath
    var i = 1
    while (true) {
        val candidate = File(dir, "$base-$i")
        if (!candidate.exists()) return candidate.absolutePath
        i++
    }
}

private fun uniqueFilePath(root: String, name: String): String {
    val dir = File(root)
    val first = File(dir, name)
    if (!first.exists()) return first.absolutePath
    val dot = name.lastIndexOf('.')
    val base = if (dot > 0) name.substring(0, dot) else name
    val ext = if (dot > 0) name.substring(dot) else ""
    var i = 1
    while (true) {
        val candidate = File(dir, "$base-$i$ext")
        if (!candidate.exists()) return candidate.absolutePath
        i++
    }
}
