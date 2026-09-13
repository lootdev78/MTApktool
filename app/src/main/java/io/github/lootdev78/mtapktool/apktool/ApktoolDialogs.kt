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
import androidx.compose.material3.RadioButton
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
    file.isFile && file.extension.lowercase() in setOf("apk", "apks", "xapk", "apkm")

fun isApktoolProject(file: File): Boolean =
    file.isDirectory && File(file, "apktool.yml").isFile

/**
 * Apktool-M style decode dialog, rendered with MTApktool's Material3 theme.
 * Runtime-only options remain backed by the Apktool-A module; explorer/container
 * handling remains in app/src.
 */
@Composable
fun ApktoolDecodeDialog(
    file: File,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val defaults = remember(file) { ApktoolSettings.decodeDefaults(context) }
    val general = remember(file) { ApktoolSettings.generalDefaults(context) }
    val isSplitArchive = SplitArchiveSupport.isSplitArchive(file)
    val baseName = file.name.replace(Regex("(?i)\\.(apk|apks|xapk|apkm)$"), "")
    val decodeRoot = if (general.decodeIntoOutputDirectory) {
        ApktoolSettings.outputRoot(context)
    } else {
        ApktoolSettings.projectsRoot(context)
    }

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
    var preserveStructure by remember { mutableStateOf(defaults.preserveDirectoryStructure) }
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

    var splitEntries by remember(file) { mutableStateOf<List<SplitArchiveSupport.ApkEntry>>(emptyList()) }
    var selectedSplitPath by remember(file) { mutableStateOf<String?>(null) }
    var splitScanning by remember(file) { mutableStateOf(isSplitArchive) }
    var splitScanError by remember(file) { mutableStateOf<String?>(null) }
    var additionalResources by remember(file) { mutableStateOf("none") }

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
        title = {
            Text(
                text = file.name,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.SemiBold,
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 590.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
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

                DialogSectionTitle("Rahmenwerk")
                ChoicePicker(
                    value = framework,
                    options = ApktoolSettings.availableFrameworkTags(context),
                    valueLabel = { ApktoolSettings.frameworkLabel(it) },
                    onSelected = { framework = it },
                )

                DialogSectionTitle("Dekompilierung zusätzlicher Ressourcen")
                val extraOptions = if (isSplitArchive) {
                    listOf("none", "separate")
                } else {
                    listOf("none")
                }
                ChoicePicker(
                    value = additionalResources,
                    options = extraOptions,
                    valueLabel = {
                        when (it) {
                            "separate" -> "Dekompilieren in ein separates Verzeichnis"
                            else -> "Nicht dekompilieren"
                        }
                    },
                    onSelected = { additionalResources = it },
                )

                if (isSplitArchive) {
                    when {
                        splitScanning -> Text(
                            "Container wird analysiert …",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 4.dp),
                        )

                        splitEntries.isNotEmpty() && additionalResources == "none" -> {
                            SplitEntryPicker(selectedSplitPath, splitEntries) { selectedSplitPath = it }
                        }

                        splitEntries.isEmpty() -> Text(
                            splitScanError ?: "Keine APK gefunden",
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }

                Text(
                    text = "Ausgabe: $output",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 8.dp),
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
                    if (resources && !onlyManifest && resolveMode != "default") {
                        flags += listOf("--res-resolve-mode", resolveMode)
                    }
                    if (matchOriginal || preserveStructure) flags += "--match-original"
                    if (resources && !onlyManifest && keepBroken) flags += "--keep-broken-res"
                    if (resources && ignoreRaw) flags += "--ignore-raw-values"
                    if (noAssets) flags += "--no-assets"

                    val allSplits = isSplitArchive && additionalResources == "separate"
                    val command = if (isSplitArchive) {
                        buildString {
                            append("apktool apks-decode ")
                            if (allSplits) append("--all-splits ")
                            else if (!selectedSplitPath.isNullOrBlank()) {
                                append("--entry ").append(ShellTokenizer.quote(selectedSplitPath!!)).append(' ')
                            }
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
            preserveStructure = preserveStructure,
            keepBroken = keepBroken,
            removeSplit = removeSplit,
            removeProperty = removeProperty,
            onNoDebug = { noDebug = it },
            onUseRegisters = { useRegisters = it },
            onPreserveStructure = { preserveStructure = it },
            onKeepBroken = { keepBroken = it },
            onRemoveSplit = { removeSplit = it },
            onRemoveProperty = { removeProperty = it },
            onThreads = { showThreads = true },
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
                        preserveDirectoryStructure = preserveStructure,
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
        ThreadPickerDialog(
            title = "Dekompilierung smali",
            value = threads,
            onSave = {
                threads = it
                ApktoolSettings.setApktoolThreads(context, it)
                showThreads = false
            },
            onDismiss = { showThreads = false },
        )
    }
}

@Composable
private fun DecodeQuickSettingsDialog(
    noDebug: Boolean,
    useRegisters: Boolean,
    preserveStructure: Boolean,
    keepBroken: Boolean,
    removeSplit: Boolean,
    removeProperty: Boolean,
    onNoDebug: (Boolean) -> Unit,
    onUseRegisters: (Boolean) -> Unit,
    onPreserveStructure: (Boolean) -> Unit,
    onKeepBroken: (Boolean) -> Unit,
    onRemoveSplit: (Boolean) -> Unit,
    onRemoveProperty: (Boolean) -> Unit,
    onThreads: () -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Einstellungen") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 570.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                CheckRow("Debug-Informationen schreiben", !noDebug) { onNoDebug(!it) }
                CheckRow("Verwenden Sie \"Register\" statt \"Lokale\".", useRegisters, onChecked = onUseRegisters)
                CheckRow("Beibehaltung der Ordnerstruktur", preserveStructure, onChecked = onPreserveStructure)
                CheckRow("Hinzufügen \"APKTOOL_DUMMY\"", true, enabled = false) { }
                CheckRow("Gebrochene Ressourcen beibehalten", keepBroken, onChecked = onKeepBroken)
                CheckRow("Gespaltene Spuren entfernen", removeSplit, onChecked = onRemoveSplit)
                CheckRow("<Eigenschaft> entfernen", removeProperty, onChecked = onRemoveProperty)
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onThreads) { Text("THREADS") }
                TextButton(onClick = onDismiss) { Text("ABBRECHEN") }
            }
        },
        confirmButton = { TextButton(onClick = onSave) { Text("SPEICHERN") } },
    )
}

/** Apktool-M style build dialog shown for folders containing apktool.yml. */
@Composable
fun ApktoolBuildDialog(
    project: File,
    onDismiss: () -> Unit,
) {
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
    var netSecKeepExisting by remember { mutableStateOf(defaults.networkSecurityKeepExisting) }
    var align by remember { mutableStateOf(defaults.zipalign) }
    var sign by remember { mutableStateOf(defaults.sign) }
    var deleteBuild by remember { mutableStateOf(defaults.deleteBuildDirectory) }
    var verbose by remember { mutableStateOf(defaults.verbose) }

    var showSettings by remember { mutableStateOf(false) }
    var showThreads by remember { mutableStateOf(false) }
    var showSignatureSchemes by remember { mutableStateOf(false) }
    var showSignatureManager by remember { mutableStateOf(false) }
    var showAaptManager by remember { mutableStateOf(false) }

    val suffix = general.apkSuffix
    val outputRoot = if (general.buildIntoOutputDirectory) {
        ApktoolSettings.outputRoot(context)
    } else {
        File(project, "dist").absolutePath
    }
    var output by remember(project, suffix, outputRoot) {
        mutableStateOf(uniqueFilePath(outputRoot, project.name + suffix + ".apk"))
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Projekt kompilieren \"${project.name}\"?") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 590.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showAaptManager = true },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = true, onClick = { showAaptManager = true })
                    Column(modifier = Modifier.weight(1f)) {
                        Text("aapt2 verwenden")
                        Text(
                            ApktoolSettings.aaptLabel(aapt),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                DialogSectionTitle("Rahmenwerk")
                ChoicePicker(
                    value = framework,
                    options = ApktoolSettings.availableFrameworkTags(context),
                    valueLabel = { ApktoolSettings.frameworkLabel(it) },
                    onSelected = { framework = it },
                )

                DialogSectionTitle("Wähle eine Signaturdatei aus")
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Box(modifier = Modifier.weight(1f)) {
                        ChoicePicker(
                            value = signature.profile,
                            options = ApktoolSettings.signatureProfiles,
                            valueLabel = {
                                if (it == "custom") {
                                    ApktoolSettings.signatureLabel(signature.copy(profile = "custom"))
                                } else {
                                    "Vorgabesignatur (testkey)"
                                }
                            },
                            onSelected = { profile ->
                                signature = signature.copy(profile = profile)
                                if (profile == "custom" && signature.customKeystorePath.isBlank()) {
                                    showSignatureManager = true
                                }
                            },
                        )
                    }
                    IconButton(onClick = { showSignatureSchemes = true }) {
                        Icon(Icons.Default.Settings, contentDescription = "Signatur einstellen")
                    }
                }

                Text(
                    text = "Ausgabe: $output",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
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
            TextButton(
                enabled = output.isNotBlank() &&
                    (aapt != "custom" || customAapt.isNotBlank()) &&
                    (!sign || signature.profile != "custom" || signature.customKeystorePath.isNotBlank()),
                onClick = {
                    val command = buildString {
                        append("apktool build")
                        append(" -j ").append(threads.coerceIn(1, 4))
                        if (verbose) append(" -v")
                        if (framework != "default") append(" -t ").append(ShellTokenizer.quote(framework))
                        if (aapt == "custom") {
                            append(" --aapt ").append(ShellTokenizer.quote(customAapt.trim()))
                        } else {
                            append(" --aapt-variant ").append(ShellTokenizer.quote(aapt))
                        }
                        if (force) append(" -f")
                        if (debuggable) append(" --debuggable")
                        if (copyOriginal) append(" --copy-original")
                        if (noCrunch) append(" --no-crunch")
                        if (netSec) {
                            append(" --net-sec-conf")
                            if (netSecKeepExisting) append(" --net-sec-conf-keep-existing")
                        }
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
            onDebuggable = { debuggable = it },
            onNetSec = { netSec = it },
            onDeleteBuild = { deleteBuild = it },
            onCopyOriginal = { copyOriginal = it },
            onThreads = { showThreads = true },
            onDismiss = { showSettings = false },
            onSave = {
                ApktoolSettings.saveBuildDefaults(
                    context,
                    ApktoolBuildDefaults(
                        force = force,
                        debuggable = debuggable,
                        copyOriginal = copyOriginal,
                        noCrunch = noCrunch,
                        networkSecurityConfig = netSec,
                        networkSecurityKeepExisting = netSecKeepExisting,
                        zipalign = align,
                        sign = sign,
                        deleteBuildDirectory = deleteBuild,
                        verbose = verbose,
                    ),
                )
                ApktoolSettings.setApktoolThreads(context, threads)
                showSettings = false
            },
        )
    }

    if (showThreads) {
        ThreadPickerDialog(
            title = "Kompilierung smali",
            value = threads,
            onSave = {
                threads = it
                ApktoolSettings.setApktoolThreads(context, it)
                showThreads = false
            },
            onDismiss = { showThreads = false },
        )
    }

    if (showSignatureSchemes) {
        SignatureSchemesDialog(
            value = signature,
            onDismiss = { showSignatureSchemes = false },
            onApply = { updated, save ->
                signature = updated
                if (save) ApktoolSettings.saveSignatureDefaults(context, updated)
                showSignatureSchemes = false
            },
        )
    }

    if (showSignatureManager) {
        SignatureManagerDialog(onBack = {
            signature = ApktoolSettings.signatureDefaults(context)
            showSignatureManager = false
        })
    }

    if (showAaptManager) {
        Aapt2ManagerDialog(onBack = {
            aapt = ApktoolSettings.aaptVariant(context)
            customAapt = ApktoolSettings.customAapt2Path(context)
            showAaptManager = false
        })
    }

}

@Composable
private fun BuildQuickSettingsDialog(
    debuggable: Boolean,
    netSec: Boolean,
    deleteBuild: Boolean,
    copyOriginal: Boolean,
    onDebuggable: (Boolean) -> Unit,
    onNetSec: (Boolean) -> Unit,
    onDeleteBuild: (Boolean) -> Unit,
    onCopyOriginal: (Boolean) -> Unit,
    onThreads: () -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Einstellungen") },
        text = {
            Column {
                CheckRow("apk als debuggingfähig einstellen", debuggable, onChecked = onDebuggable)
                CheckRow("Netzwerksicherheitskonfiguration hinzufügen", netSec, onChecked = onNetSec)
                CheckRow("Ordner \"build\" löschen", deleteBuild, onChecked = onDeleteBuild)
                CheckRow("Ersetzen von Prüfsummen aus dem Original", copyOriginal, onChecked = onCopyOriginal)
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onThreads) { Text("THREADS") }
                TextButton(onClick = onDismiss) { Text("ABBRECHEN") }
            }
        },
        confirmButton = { TextButton(onClick = onSave) { Text("SPEICHERN") } },
    )
}

@Composable
private fun SignatureSchemesDialog(
    value: ApktoolSignatureDefaults,
    onDismiss: () -> Unit,
    onApply: (ApktoolSignatureDefaults, Boolean) -> Unit,
) {
    var v1 by remember(value) { mutableStateOf(value.v1) }
    var v2 by remember(value) { mutableStateOf(value.v2) }
    var v3 by remember(value) { mutableStateOf(value.v3) }
    var v4 by remember(value) { mutableStateOf(value.v4) }
    var save by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Signatur") },
        text = {
            Column {
                CheckRow("Signatur v1", v1) { v1 = it }
                CheckRow("Signatur v2", v2) { v2 = it }
                CheckRow("Signatur v3", v3) { v3 = it }
                CheckRow("Signatur v4", v4) { v4 = it }
                CheckRow("Speichern", save) { save = it }
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { }) { Text("INFO") }
                TextButton(onClick = onDismiss) { Text("ABBRECHEN") }
            }
        },
        confirmButton = {
            TextButton(
                enabled = v1 || v2 || v3 || v4,
                onClick = { onApply(value.copy(v1 = v1, v2 = v2, v3 = v3, v4 = v4), save) },
            ) { Text("OK") }
        },
    )
}

@Composable
private fun ThreadPickerDialog(
    title: String,
    value: Int,
    onSave: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var selected by remember(value) { mutableIntStateOf(value.coerceIn(1, 4)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                (1..4).forEach { n ->
                    val selectedNow = selected == n
                    TextButton(onClick = { selected = n }, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "$n",
                            style = if (selectedNow) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleMedium,
                            color = if (selectedNow) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onDismiss) { Text("ABBRECHEN") }
                TextButton(onClick = { selected = 2 }) { Text("ZURÜCKSETZEN") }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(selected) }) { Text("SPEICHERN") } },
    )
}

@Composable
fun ApktoolJobsDialog(
    jobs: List<ApktoolJobInfo>,
    onCancel: (String) -> Unit,
    onCancelAll: () -> Unit,
    onDismiss: () -> Unit,
) {
    val active = jobs.count { !it.isTerminal }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Apktool Jobs${if (active > 0) " ($active aktiv)" else ""}") },
        text = {
            if (jobs.isEmpty()) {
                Text("Keine Jobs in dieser App-Sitzung.")
            } else {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 520.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(jobs, key = { it.id }) { job ->
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(10.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        job.title,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.weight(1f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(job.status, style = MaterialTheme.typography.labelSmall)
                                }
                                Text(
                                    job.line.lineSequence().lastOrNull().orEmpty(),
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(top = 4.dp),
                                )
                                if (!job.isTerminal) {
                                    TextButton(
                                        onClick = { onCancel(job.id) },
                                        modifier = Modifier.align(Alignment.End),
                                    ) { Text("STOP") }
                                } else if (!job.output.isNullOrBlank()) {
                                    Text(
                                        job.output,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontFamily = FontFamily.Monospace,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        dismissButton = {
            if (active > 0) TextButton(onClick = onCancelAll) { Text("ALLE STOPPEN") }
        },
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
                Text(
                    "decode/build/framework/zipalign/apksigner, --use-registers und apktool-original werden unterstützt.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("ABBRECHEN") } },
        confirmButton = {
            Button(
                enabled = command.isNotBlank(),
                onClick = {
                    ApktoolJobService.enqueue(context, "CLI", command)
                    onDismiss()
                },
            ) { Text("START") }
        },
    )
}

@Composable
private fun DialogSectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
    )
}

@Composable
private fun CheckRow(
    label: String,
    checked: Boolean,
    enabled: Boolean = true,
    onChecked: (Boolean) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onChecked(!checked) },
    ) {
        Checkbox(checked = checked, onCheckedChange = onChecked, enabled = enabled)
        Text(
            label,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.45f),
        )
    }
}

@Composable
private fun SplitEntryPicker(
    value: String?,
    entries: List<SplitArchiveSupport.ApkEntry>,
    onSelected: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = entries.firstOrNull { it.path == value } ?: entries.firstOrNull()
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.weight(1f)) {
            Text("APK im Container", style = MaterialTheme.typography.labelLarge)
            Text(
                selected?.path ?: "Automatisch",
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Box {
            TextButton(onClick = { expanded = true }) {
                Text(if (selected?.preferred == true) "Base (auto)" else "Auswählen")
            }
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
                        onClick = {
                            onSelected(entry.path)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ChoicePicker(
    value: String,
    options: List<String>,
    valueLabel: (String) -> String,
    enabled: Boolean = true,
    onSelected: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = Modifier.fillMaxWidth()) {
        TextButton(
            onClick = { expanded = true },
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(valueLabel(value), modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.width(8.dp))
                Text("▾", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.distinct().forEach { option ->
                DropdownMenuItem(
                    text = { Text(valueLabel(option)) },
                    onClick = {
                        expanded = false
                        onSelected(option)
                    },
                )
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
