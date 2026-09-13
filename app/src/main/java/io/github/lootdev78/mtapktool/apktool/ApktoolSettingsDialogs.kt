package io.github.lootdev78.mtapktool.apktool

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.apktool.android.runtime.ApktoolCommandRunner
import io.github.apktool.android.runtime.ShellTokenizer
import io.github.apktool.android.runtime.Toolchain
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class SettingsPage {
    HOME, GENERAL, FRAMEWORKS, AAPT2, DECODE, BUILD, SIGNATURE, PATHS, RUNTIME
}

@Composable
fun ApktoolSettingsDialog(onDismiss: () -> Unit) {
    var page by remember { mutableStateOf(SettingsPage.HOME) }
    when (page) {
        SettingsPage.HOME -> SettingsHomeDialog(onDismiss = onDismiss, onOpen = { page = it })
        SettingsPage.GENERAL -> GeneralDefaultsDialog(onBack = { page = SettingsPage.HOME })
        SettingsPage.FRAMEWORKS -> FrameworkManagerDialog(onBack = { page = SettingsPage.HOME })
        SettingsPage.AAPT2 -> Aapt2ManagerDialog(onBack = { page = SettingsPage.HOME })
        SettingsPage.DECODE -> DecodeDefaultsDialog(onBack = { page = SettingsPage.HOME })
        SettingsPage.BUILD -> BuildDefaultsDialog(
            onBack = { page = SettingsPage.HOME },
            onSignature = { page = SettingsPage.SIGNATURE },
        )
        SettingsPage.SIGNATURE -> SignatureManagerDialog(onBack = { page = SettingsPage.BUILD })
        SettingsPage.PATHS -> PathsAndJobsDialog(onBack = { page = SettingsPage.HOME })
        SettingsPage.RUNTIME -> RuntimeInfoDialog(onBack = { page = SettingsPage.HOME })
    }
}

@Composable
private fun SettingsHomeDialog(onDismiss: () -> Unit, onOpen: (SettingsPage) -> Unit) {
    val context = LocalContext.current
    val framework = ApktoolSettings.frameworkTag(context)
    val aapt = ApktoolSettings.aaptVariant(context)
    val workers = ApktoolSettings.maxWorkers(context)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("MTApktool Einstellungen") },
        text = {
            LazyColumn(modifier = Modifier.heightIn(max = 580.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                item {
                    SettingsEntry("Erstellen & Dekodieren", "Benachrichtigung, Suffix und Ausgabeverzeichnisse") {
                        onOpen(SettingsPage.GENERAL)
                    }
                }
                item {
                    SettingsEntry("Framework Manager", "Aktiv: ${ApktoolSettings.frameworkLabel(framework)}") {
                        onOpen(SettingsPage.FRAMEWORKS)
                    }
                }
                item {
                    SettingsEntry("AAPT2 Manager", "Aktiv: ${ApktoolSettings.aaptLabel(aapt)} • AAPT1 deaktiviert") {
                        onOpen(SettingsPage.AAPT2)
                    }
                }
                item {
                    SettingsEntry("Dekompilieren", "Smali, Ressourcen, APKTOOL_DUMMY-nahe und Split-Optionen") {
                        onOpen(SettingsPage.DECODE)
                    }
                }
                item {
                    SettingsEntry("Kompilieren", "Build, AAPT2, Netzwerk, Zipalign und Signatur") {
                        onOpen(SettingsPage.BUILD)
                    }
                }
                item {
                    SettingsEntry("Pfade & Jobs", "$workers parallele Runner • maximal 4") {
                        onOpen(SettingsPage.PATHS)
                    }
                }
                item {
                    SettingsEntry("Runtime", "SDK 36 • NDK 29.0.14033849 • arm64-v8a") {
                        onOpen(SettingsPage.RUNTIME)
                    }
                }
            }
        },
        dismissButton = {
            TextButton(onClick = {
                ApktoolSettings.resetDefaults(context)
                onDismiss()
            }) { Text("ZURÜCKSETZEN") }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("SCHLIESSEN") } },
    )
}

@Composable
private fun GeneralDefaultsDialog(onBack: () -> Unit) {
    val context = LocalContext.current
    val defaults = remember { ApktoolSettings.generalDefaults(context) }
    var notify by remember { mutableStateOf(defaults.notifyOnCompletion) }
    var hideWhileOpen by remember { mutableStateOf(defaults.suppressCompletionWhileOpen) }
    var suffix by remember { mutableStateOf(defaults.apkSuffix) }
    var decodeToOutput by remember { mutableStateOf(defaults.decodeIntoOutputDirectory) }
    var buildToOutput by remember { mutableStateOf(defaults.buildIntoOutputDirectory) }

    AlertDialog(
        onDismissRequest = onBack,
        title = { Text("Erstellen & Dekodieren") },
        text = {
            Column(modifier = Modifier.heightIn(max = 570.dp).verticalScroll(rememberScrollState())) {
                SettingCheck("Nach Abschluss benachrichtigen", notify) { notify = it }
                SettingHint("Sendet nach abgeschlossener Erstellung oder Dekompilierung eine Meldung.")
                SettingCheck("Nicht benachrichtigen, falls ausgeführt", hideWhileOpen, enabled = notify) { hideWhileOpen = it }
                SettingHint("Keine Abschlussmeldung, solange MTApktool sichtbar ist. Die Android-Foreground-Service-Meldung bleibt systembedingt bestehen.")
                OutlinedTextField(
                    value = suffix,
                    onValueChange = { suffix = it },
                    label = { Text("Suffix für APK") },
                    supportingText = { Text("Wird an den Build-Dateinamen angehängt.") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                SettingCheck("Alles im Ausgabeverzeichnis", decodeToOutput) { decodeToOutput = it }
                SettingHint("Dekompilierte Projekte verwenden standardmäßig den Ausgabeordner statt /apktool/projects.")
                SettingCheck("Erstellen im Ausgabeverzeichnis", buildToOutput) { buildToOutput = it }
                SettingHint("Kompilierte APKs werden standardmäßig unter /apktool/output angelegt.")
                Spacer(Modifier.height(6.dp))
                Text("Frameworks und Werkzeuge werden in den separaten Managern verwaltet.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        dismissButton = { TextButton(onClick = onBack) { Text("ABBRECHEN") } },
        confirmButton = {
            Button(onClick = {
                ApktoolSettings.saveGeneralDefaults(
                    context,
                    ApktoolGeneralDefaults(notify, hideWhileOpen, suffix, decodeToOutput, buildToOutput),
                )
                onBack()
            }) { Text("SPEICHERN") }
        },
    )
}

@Composable
private fun FrameworkManagerDialog(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var active by remember { mutableStateOf(ApktoolSettings.frameworkTag(context)) }
    var installPath by remember { mutableStateOf("") }
    var installTag by remember { mutableStateOf("") }
    var frameworks by remember { mutableStateOf<List<File>>(emptyList()) }
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
    var status by remember { mutableStateOf("") }
    var refreshToken by remember { mutableIntStateOf(0) }

    fun refresh() { refreshToken++ }

    fun runFrameworkCommand(command: String) {
        scope.launch {
            status = "Bitte warten …"
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val toolchain = Toolchain(context)
                    toolchain.provision()
                    ApktoolCommandRunner(toolchain) { }.execute(command).summary
                }
            }
            status = result.getOrElse { it.message ?: it.toString() }
            selected = emptySet()
            refresh()
        }
    }

    val frameworkPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                val result = withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            Toolchain(context).copyIntoInput(input, "framework.apk").absolutePath
                        } ?: error("Framework-Datei kann nicht geöffnet werden")
                    }
                }
                result.onSuccess { installPath = it; status = "Framework ausgewählt." }
                    .onFailure { status = it.message ?: it.toString() }
            }
        }
    }

    LaunchedEffect(refreshToken) {
        val result = withContext(Dispatchers.IO) {
            runCatching { Toolchain(context).apply { provision() }.listFrameworks() }
        }
        frameworks = result.getOrDefault(emptyList())
        if (result.isFailure) status = result.exceptionOrNull()?.message.orEmpty()
    }

    AlertDialog(
        onDismissRequest = onBack,
        title = { Text("Verwaltung der installierten Frameworks") },
        text = {
            Column(modifier = Modifier.heightIn(max = 600.dp)) {
                Text("Verwendetes Framework", style = MaterialTheme.typography.labelLarge)
                CompactPicker(
                    value = active,
                    options = ApktoolSettings.frameworkOptions,
                    label = { ApktoolSettings.frameworkLabel(it) },
                    onSelected = {
                        active = it
                        ApktoolSettings.setFrameworkTag(context, it)
                    },
                )
                Spacer(Modifier.height(6.dp))
                Text("Installierte Frameworks", fontWeight = FontWeight.SemiBold)
                if (frameworks.isEmpty()) {
                    Text("Keine Framework-Dateien gefunden.", style = MaterialTheme.typography.bodySmall)
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 210.dp)) {
                        items(frameworks, key = { it.absolutePath }) { file ->
                            val checked = file.absolutePath in selected
                            Row(
                                modifier = Modifier.fillMaxWidth().clickable {
                                    selected = if (checked) selected - file.absolutePath else selected + file.absolutePath
                                },
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(checked = checked, onCheckedChange = {
                                    selected = if (it) selected + file.absolutePath else selected - file.absolutePath
                                })
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(frameworkDisplayName(file), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        "${frameworkSdkLabel(file)} • %.2f MiB".format(file.length() / 1024.0 / 1024.0),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = installPath,
                    onValueChange = { installPath = it },
                    label = { Text("Framework APK") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { frameworkPicker.launch(arrayOf("application/vnd.android.package-archive", "application/octet-stream", "application/zip")) }) {
                        Text("APK AUSWÄHLEN")
                    }
                    OutlinedTextField(
                        value = installTag,
                        onValueChange = { installTag = it },
                        label = { Text("Tag") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }
                TextButton(
                    enabled = installPath.isNotBlank(),
                    onClick = {
                        val command = buildString {
                            append("apktool install-framework -p ").append(ShellTokenizer.quote(ApktoolSettings.frameworkDir()))
                            if (installTag.isNotBlank()) append(" -t ").append(ShellTokenizer.quote(installTag.trim()))
                            append(' ').append(ShellTokenizer.quote(installPath.trim()))
                        }
                        ApktoolJobService.enqueue(context, "Framework installieren", command)
                        status = "Framework-Installation wurde als Job gestartet."
                    },
                ) { Text("INSTALLIEREN") }
                if (status.isNotBlank()) Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onBack) { Text("SCHLIESSEN") }
                TextButton(
                    enabled = selected.isNotEmpty(),
                    onClick = {
                        val names = selected.map { File(it).name }
                        val command = "apktool delete-frameworks " + names.joinToString(" ") { ShellTokenizer.quote(it) }
                        runFrameworkCommand(command)
                    },
                ) { Text("LÖSCHEN") }
            }
        },
        confirmButton = {
            TextButton(onClick = { runFrameworkCommand("apktool reset-frameworks") }) { Text("ZURÜCKSETZEN") }
        },
    )
}

@Composable
private fun Aapt2ManagerDialog(onBack: () -> Unit) {
    val context = LocalContext.current
    var selected by remember { mutableStateOf(ApktoolSettings.aaptVariant(context)) }
    var customPath by remember { mutableStateOf(ApktoolSettings.customAapt2Path(context)) }
    var runtimeInfo by remember { mutableStateOf("") }

    LaunchedEffect(selected, customPath) {
        runtimeInfo = withContext(Dispatchers.IO) {
            runCatching {
                if (selected == "custom") {
                    val f = File(customPath)
                    if (!f.isFile) "Benutzerdefiniertes AAPT2 nicht gefunden"
                    else "${f.absolutePath}\n%.2f MiB".format(f.length() / 1024.0 / 1024.0)
                } else {
                    val toolchain = Toolchain(context)
                    toolchain.provision()
                    val file = toolchain.getAaptBinary(selected)
                    "${file.absolutePath}\n%.2f MiB • Page ${toolchain.runtimePageSize / 1024} KiB".format(file.length() / 1024.0 / 1024.0)
                }
            }.getOrElse { it.message ?: it.toString() }
        }
    }

    AlertDialog(
        onDismissRequest = onBack,
        title = { Text("AAPT2 Manager") },
        text = {
            Column(modifier = Modifier.heightIn(max = 560.dp)) {
                Text("AAPT1 ist absichtlich nicht verfügbar.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                CompactPicker(
                    value = selected,
                    options = ApktoolSettings.aaptOptions,
                    label = { ApktoolSettings.aaptLabel(it) },
                    onSelected = { selected = it },
                )
                if (selected == "custom") {
                    OutlinedTextField(
                        value = customPath,
                        onValueChange = { customPath = it },
                        label = { Text("AAPT2 Pfad") },
                        supportingText = { Text("Muss auf Android tatsächlich ausführbar sein.") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                }
                Card(modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
                    Column(Modifier.padding(10.dp)) {
                        Text("Aktive Binary", fontWeight = FontWeight.SemiBold)
                        Text(runtimeInfo, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                SettingHint("Automatisch nutzt auf 16-KiB-Geräten die kompatible SDK33-Payload und sonst die neuere SDK35-Payload. SDK36 wählt die kompatible Payload für das Gerät.")
            }
        },
        dismissButton = { TextButton(onClick = onBack) { Text("ABBRECHEN") } },
        confirmButton = {
            Button(
                enabled = selected != "custom" || customPath.isNotBlank(),
                onClick = {
                    ApktoolSettings.setAapt2(context, selected, customPath)
                    onBack()
                },
            ) { Text("SPEICHERN") }
        },
    )
}

@Composable
private fun DecodeDefaultsDialog(onBack: () -> Unit) {
    val context = LocalContext.current
    val defaults = remember { ApktoolSettings.decodeDefaults(context) }
    var force by remember { mutableStateOf(defaults.force) }
    var allSources by remember { mutableStateOf(defaults.allSources) }
    var noSources by remember { mutableStateOf(defaults.noSources) }
    var noDebug by remember { mutableStateOf(defaults.noDebugInfo) }
    var noResources by remember { mutableStateOf(defaults.noResources) }
    var onlyManifest by remember { mutableStateOf(defaults.onlyManifest) }
    var matchOriginal by remember { mutableStateOf(defaults.matchOriginal) }
    var keepBroken by remember { mutableStateOf(defaults.keepBrokenResources) }
    var ignoreRaw by remember { mutableStateOf(defaults.ignoreRawValues) }
    var noAssets by remember { mutableStateOf(defaults.noAssets) }
    var resolveMode by remember { mutableStateOf(defaults.resourceResolveMode) }
    var useRegisters by remember { mutableStateOf(defaults.useRegisters) }
    var nomedia by remember { mutableStateOf(defaults.createNomedia) }
    var removeSplit by remember { mutableStateOf(defaults.removeSplitTraces) }
    var removeProperty by remember { mutableStateOf(defaults.removePropertyTags) }
    var verbose by remember { mutableStateOf(defaults.verbose) }

    AlertDialog(
        onDismissRequest = onBack,
        title = { Text("Dekompilieren – Einstellungen") },
        text = {
            LazyColumn(modifier = Modifier.heightIn(max = 585.dp)) {
                item { SettingCheck("Debug-Informationen schreiben", !noDebug, !noSources) { noDebug = !it } }
                item { SettingCheck("Register statt Lokale verwenden", useRegisters, !noSources) { useRegisters = it } }
                item { SettingHint("Erzeugt .registers statt .locals in Smali.") }
                item { SettingCheck("Original anpassen", matchOriginal) { matchOriginal = it } }
                item { SettingHint("Hält Dateien so nah wie Apktool möglich am Original; kann einen späteren Rebuild erschweren.") }
                item { SettingCheck("APKTOOL_DUMMY zulassen", true, enabled = false) { } }
                item { SettingHint("Apktool 3.x erzeugt APKTOOL_DUMMY automatisch für fehlende Ressourcenreferenzen; dieses Verhalten ist im Port aktiv.") }
                item { SettingCheck("Gebrochene Ressourcen beibehalten", keepBroken, !noResources && !onlyManifest) { keepBroken = it } }
                item { SettingCheck("Gespaltene Spuren entfernen", removeSplit) { removeSplit = it } }
                item { SettingCheck("<property> entfernen", removeProperty) { removeProperty = it } }
                item { SettingCheck(".nomedia im Projekt anlegen", nomedia) { nomedia = it } }
                item { SettingCheck("Ausführlich", verbose) { verbose = it } }
                item { SettingCheck("Vorhandenes Projekt überschreiben", force) { force = it } }
                item { SettingCheck("Alle *.dex dekompilieren", allSources) { allSources = it; if (it) noSources = false } }
                item { SettingCheck("Smali nicht dekompilieren", noSources) { noSources = it; if (it) { allSources = false; noDebug = false; useRegisters = false } } }
                item { SettingCheck("Ressourcen nicht dekompilieren", noResources) { noResources = it; if (it) onlyManifest = false } }
                item { SettingCheck("Nur AndroidManifest.xml", onlyManifest, !noResources) { onlyManifest = it } }
                item {
                    Column(Modifier.padding(vertical = 4.dp)) {
                        Text("Resource resolve mode", style = MaterialTheme.typography.labelLarge)
                        CompactPicker(resolveMode, ApktoolSettings.resourceResolveModes, { it }) { resolveMode = it }
                    }
                }
                item { SettingCheck("Raw values ignorieren", ignoreRaw, !noResources) { ignoreRaw = it } }
                item { SettingCheck("Assets nicht dekompilieren", noAssets) { noAssets = it } }
            }
        },
        dismissButton = { TextButton(onClick = onBack) { Text("ABBRECHEN") } },
        confirmButton = {
            Button(onClick = {
                ApktoolSettings.saveDecodeDefaults(
                    context,
                    ApktoolDecodeDefaults(
                        force = force,
                        allSources = allSources,
                        noSources = noSources,
                        noDebugInfo = noDebug,
                        noResources = noResources,
                        onlyManifest = onlyManifest,
                        matchOriginal = matchOriginal,
                        keepBrokenResources = keepBroken,
                        ignoreRawValues = ignoreRaw,
                        noAssets = noAssets,
                        resourceResolveMode = resolveMode,
                        useRegisters = useRegisters,
                        createNomedia = nomedia,
                        removeSplitTraces = removeSplit,
                        removePropertyTags = removeProperty,
                        verbose = verbose,
                    ),
                )
                onBack()
            }) { Text("SPEICHERN") }
        },
    )
}

@Composable
private fun BuildDefaultsDialog(onBack: () -> Unit, onSignature: () -> Unit) {
    val context = LocalContext.current
    val defaults = remember { ApktoolSettings.buildDefaults(context) }
    var force by remember { mutableStateOf(defaults.force) }
    var debuggable by remember { mutableStateOf(defaults.debuggable) }
    var copyOriginal by remember { mutableStateOf(defaults.copyOriginal) }
    var noCrunch by remember { mutableStateOf(defaults.noCrunch) }
    var netSec by remember { mutableStateOf(defaults.networkSecurityConfig) }
    var align by remember { mutableStateOf(defaults.zipalign) }
    var sign by remember { mutableStateOf(defaults.sign) }
    var deleteBuild by remember { mutableStateOf(defaults.deleteBuildDirectory) }
    var verbose by remember { mutableStateOf(defaults.verbose) }

    AlertDialog(
        onDismissRequest = onBack,
        title = { Text("Kompilieren – Einstellungen") },
        text = {
            Column(modifier = Modifier.heightIn(max = 570.dp).verticalScroll(rememberScrollState())) {
                SettingCheck("APK als debuggingfähig einstellen", debuggable) { debuggable = it }
                SettingCheck("Netzwerksicherheitskonfiguration hinzufügen", netSec) { netSec = it }
                SettingCheck("Ordner \"build\" löschen", deleteBuild) { deleteBuild = it }
                SettingCheck("Originaldateien/Prüfsummen übernehmen", copyOriginal) { copyOriginal = it }
                SettingHint("Entspricht Apktool --copy-original und übernimmt Original-Manifest/META-INF, soweit Apktool dies unterstützt.")
                SettingCheck("Force build", force) { force = it }
                SettingCheck("No crunch", noCrunch) { noCrunch = it }
                SettingCheck("Ausführlich", verbose) { verbose = it }
                Spacer(Modifier.height(5.dp))
                Text("Nach dem Build", fontWeight = FontWeight.SemiBold)
                SettingCheck("Zipalign (16 KiB page aware)", align) { align = it }
                SettingCheck("Signieren", sign) { sign = it }
                SettingsEntry("Signatur", ApktoolSettings.signatureLabel(ApktoolSettings.signatureDefaults(context))) { onSignature() }
                SettingHint("AAPT2 wird im AAPT2 Manager gewählt. AAPT1 wird nicht angeboten.")
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onSignature) { Text("SIGNATUR") }
                TextButton(onClick = onBack) { Text("ABBRECHEN") }
            }
        },
        confirmButton = {
            Button(onClick = {
                ApktoolSettings.saveBuildDefaults(
                    context,
                    ApktoolBuildDefaults(force, debuggable, copyOriginal, noCrunch, netSec, align, sign, deleteBuild, verbose),
                )
                onBack()
            }) { Text("SPEICHERN") }
        },
    )
}

@Composable
fun SignatureManagerDialog(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val defaults = remember { ApktoolSettings.signatureDefaults(context) }
    var profile by remember { mutableStateOf(defaults.profile) }
    var path by remember { mutableStateOf(defaults.customKeystorePath) }
    var password by remember { mutableStateOf(defaults.customKeystorePassword) }
    var v1 by remember { mutableStateOf(defaults.v1) }
    var v2 by remember { mutableStateOf(defaults.v2) }
    var v3 by remember { mutableStateOf(defaults.v3) }
    var v4 by remember { mutableStateOf(defaults.v4) }
    var status by remember { mutableStateOf("") }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                val result = withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            Toolchain(context).copyIntoInput(input, "signature.jks").absolutePath
                        } ?: error("Signaturdatei kann nicht geöffnet werden")
                    }
                }
                result.onSuccess { path = it; profile = "custom"; status = "Signaturdatei ausgewählt." }
                    .onFailure { status = it.message ?: it.toString() }
            }
        }
    }

    AlertDialog(
        onDismissRequest = onBack,
        title = { Text("Signatur") },
        text = {
            Column(modifier = Modifier.heightIn(max = 570.dp).verticalScroll(rememberScrollState())) {
                CompactPicker(
                    value = profile,
                    options = ApktoolSettings.signatureProfiles,
                    label = { if (it == "testkey") "Vorgabesignatur (testkey)" else "Benutzerdefinierte Signatur" },
                    onSelected = { profile = it },
                )
                if (profile == "custom") {
                    OutlinedTextField(
                        value = path,
                        onValueChange = { path = it },
                        label = { Text("Keystore") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    TextButton(onClick = { picker.launch(arrayOf("application/octet-stream", "application/x-pkcs12", "*/*")) }) { Text("DATEI AUSWÄHLEN") }
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Keystore-Passwort") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                SettingCheck("Signatur v1", v1) { v1 = it }
                SettingCheck("Signatur v2", v2) { v2 = it }
                SettingCheck("Signatur v3", v3) { v3 = it }
                SettingCheck("Signatur v4", v4) { v4 = it }
                SettingHint("Mindestens ein Signaturschema muss aktiv sein. V4 kann eine zusätzliche .idsig-Datei erzeugen, abhängig vom Signer.")
                if (status.isNotBlank()) Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        dismissButton = { TextButton(onClick = onBack) { Text("ABBRECHEN") } },
        confirmButton = {
            Button(
                enabled = (v1 || v2 || v3 || v4) && (profile != "custom" || path.isNotBlank()),
                onClick = {
                    ApktoolSettings.saveSignatureDefaults(context, ApktoolSignatureDefaults(profile, path, password, v1, v2, v3, v4))
                    onBack()
                },
            ) { Text("SPEICHERN") }
        },
    )
}

@Composable
private fun PathsAndJobsDialog(onBack: () -> Unit) {
    val context = LocalContext.current
    var workers by remember { mutableIntStateOf(ApktoolSettings.maxWorkers(context)) }
    var threads by remember { mutableIntStateOf(ApktoolSettings.apktoolThreads(context)) }
    var projects by remember { mutableStateOf(ApktoolSettings.projectsRoot(context)) }
    var output by remember { mutableStateOf(ApktoolSettings.outputRoot(context)) }

    AlertDialog(
        onDismissRequest = onBack,
        title = { Text("Pfade & Jobs") },
        text = {
            Column(modifier = Modifier.heightIn(max = 570.dp).verticalScroll(rememberScrollState())) {
                Text("Parallele Runner", fontWeight = FontWeight.SemiBold)
                NumberPickerRow(value = workers, range = 1..4) { workers = it }
                SettingHint("Maximal vier Decode/Build-Jobs gleichzeitig. Jeder Job kann einzeln gestoppt werden.")
                Text("Apktool Threads pro Job", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 6.dp))
                NumberPickerRow(value = threads, range = 1..4) { threads = it }
                SettingHint("Entspricht -j/--jobs für Smali/Build. Bei vier parallelen Runnern kann ein niedrigerer Wert RAM und CPU deutlich entlasten.")
                OutlinedTextField(
                    value = projects,
                    onValueChange = { projects = it },
                    label = { Text("Projects root") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                OutlinedTextField(
                    value = output,
                    onValueChange = { output = it },
                    label = { Text("Build output root") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                )
            }
        },
        dismissButton = { TextButton(onClick = onBack) { Text("ABBRECHEN") } },
        confirmButton = {
            Button(onClick = {
                ApktoolSettings.savePathsAndWorkers(context, workers, projects, output, threads)
                onBack()
            }) { Text("SPEICHERN") }
        },
    )
}

@Composable
private fun RuntimeInfoDialog(onBack: () -> Unit) {
    val context = LocalContext.current
    var info by remember { mutableStateOf("Lade Runtime …") }
    LaunchedEffect(Unit) {
        info = withContext(Dispatchers.IO) {
            runCatching {
                val toolchain = Toolchain(context)
                toolchain.provision()
                buildString {
                    append("MTApktool runtime\n")
                    append("Apktool: ").append(Toolchain.VERSION).append('\n')
                    append("compileSdk/targetSdk: 36\n")
                    append("NDK: 29.0.14033849\n")
                    append("ABI: arm64-v8a\n")
                    append("Runtime page: ").append(toolchain.runtimePageSize / 1024).append(" KiB\n")
                    append("Runner: ").append(ApktoolSettings.maxWorkers(context)).append("/4\n")
                    append("Threads/job: ").append(ApktoolSettings.apktoolThreads(context)).append("/4\n")
                    append("Root: ").append(toolchain.root.absolutePath).append('\n')
                    append("Frameworks: ").append(toolchain.frameworkDir.absolutePath).append('\n')
                    append("AAPT2 mirror: ").append(ApktoolSettings.aaptMirrorDir())
                }
            }.getOrElse { it.stackTraceToString() }
        }
    }
    AlertDialog(
        onDismissRequest = onBack,
        title = { Text("Runtime") },
        text = { Text(info, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall) },
        confirmButton = { TextButton(onClick = onBack) { Text("ZURÜCK") } },
    )
}

@Composable
private fun SettingsEntry(title: String, subtitle: String, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun SettingCheck(label: String, checked: Boolean, enabled: Boolean = true, onChecked: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(enabled = enabled) { onChecked(!checked) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = onChecked, enabled = enabled)
        Text(label, color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.4f))
    }
}

@Composable
private fun SettingHint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 48.dp, bottom = 5.dp),
    )
}

@Composable
private fun NumberPickerRow(value: Int, range: IntRange, onSelected: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 5.dp)) {
        range.forEach { n ->
            if (value == n) Button(onClick = { onSelected(n) }) { Text("$n") }
            else TextButton(onClick = { onSelected(n) }) { Text("$n") }
        }
    }
}

@Composable
private fun CompactPicker(value: String, options: List<String>, label: (String) -> String, onSelected: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = Modifier.fillMaxWidth()) {
        TextButton(onClick = { expanded = true }, modifier = Modifier.align(Alignment.CenterEnd)) { Text(label(value)) }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(label(option)) },
                    onClick = { expanded = false; onSelected(option) },
                )
            }
        }
    }
}

private fun frameworkDisplayName(file: File): String = file.name

private fun frameworkSdkLabel(file: File): String {
    val sdk = Regex("sdk(\\d+)", RegexOption.IGNORE_CASE).find(file.name)?.groupValues?.getOrNull(1)
    return when {
        sdk != null -> "SDK $sdk"
        file.name == "1.apk" -> "SDK 36"
        else -> "Framework"
    }
}
