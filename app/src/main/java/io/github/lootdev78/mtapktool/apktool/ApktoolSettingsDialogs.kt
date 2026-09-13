package io.github.lootdev78.mtapktool.apktool

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import brut.androlib.res.AaptManager
import io.github.apktool.android.runtime.ApktoolCommandRunner
import io.github.apktool.android.runtime.ShellTokenizer
import io.github.apktool.android.runtime.Toolchain
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class SettingsOverlay { NONE, FRAMEWORKS, AAPT2, SIGNATURE, PATHS, RUNTIME }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApktoolSettingsDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var general by remember { mutableStateOf(ApktoolSettings.generalDefaults(context)) }
    var decode by remember { mutableStateOf(ApktoolSettings.decodeDefaults(context)) }
    var build by remember { mutableStateOf(ApktoolSettings.buildDefaults(context)) }
    var framework by remember { mutableStateOf(ApktoolSettings.frameworkTag(context)) }
    var aapt by remember { mutableStateOf(ApktoolSettings.aaptVariant(context)) }
    var overlay by remember { mutableStateOf(SettingsOverlay.NONE) }
    var suffixDialog by remember { mutableStateOf(false) }
    var outputDialog by remember { mutableStateOf(false) }
    var overflow by remember { mutableStateOf(false) }

    fun saveGeneral(value: ApktoolGeneralDefaults) { general = value; ApktoolSettings.saveGeneralDefaults(context, value) }
    fun saveDecode(value: ApktoolDecodeDefaults) { decode = value; ApktoolSettings.saveDecodeDefaults(context, value) }
    fun saveBuild(value: ApktoolBuildDefaults) { build = value; ApktoolSettings.saveBuildDefaults(context, value) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text("Erstellen & Dekodieren") },
                        navigationIcon = {
                            IconButton(onClick = onDismiss) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück") }
                        },
                        actions = {
                            Box {
                                IconButton(onClick = { overflow = true }) { Icon(Icons.Default.MoreVert, contentDescription = "Mehr") }
                                DropdownMenu(expanded = overflow, onDismissRequest = { overflow = false }) {
                                    DropdownMenuItem(
                                        text = { Text("Zurücksetzen") },
                                        onClick = {
                                            overflow = false
                                            ApktoolSettings.resetDefaults(context)
                                            general = ApktoolSettings.generalDefaults(context)
                                            decode = ApktoolSettings.decodeDefaults(context)
                                            build = ApktoolSettings.buildDefaults(context)
                                            framework = ApktoolSettings.frameworkTag(context)
                                            aapt = ApktoolSettings.aaptVariant(context)
                                        },
                                    )
                                }
                            }
                        },
                    )
                },
            ) { insets ->
                LazyColumn(modifier = Modifier.fillMaxSize().padding(insets)) {
                    item { SettingSwitchRow("Nach Abschluss benachrichtigen", "Benachrichtigung nach Erstellen/Dekompilieren", general.notifyOnCompletion) { saveGeneral(general.copy(notifyOnCompletion = it)) } }
                    item { SettingSwitchRow("Nicht benachrichtigen, falls ausgeführt", "Keine Abschlussmeldung, solange MTApktool sichtbar ist", general.suppressCompletionWhileOpen, general.notifyOnCompletion) { saveGeneral(general.copy(suppressCompletionWhileOpen = it)) } }
                    item { SettingValueRow("Suffix für apk", "Suffix für die Ausgabe-APK", general.apkSuffix.ifBlank { "Kein Suffix" }) { suffixDialog = true } }
                    item { SettingSwitchRow("Ordner \"build\" löschen", "Build-Verzeichnis nach erfolgreicher Kompilierung entfernen", build.deleteBuildDirectory) { saveBuild(build.copy(deleteBuildDirectory = it)) } }
                    item { SettingSwitchRow("Vollständigen Build erzwingen", "Apktool --force: alle Build-Schritte erneut ausführen", build.force) { saveBuild(build.copy(force = it)) } }
                    item { SettingSwitchRow("Resource-Crunching deaktivieren", "Apktool --no-crunch", build.noCrunch) { saveBuild(build.copy(noCrunch = it)) } }
                    item { SettingSwitchRow("Originaldateien kopieren", "Apktool --copy-original für Manifest/META-INF", build.copyOriginal) { saveBuild(build.copy(copyOriginal = it)) } }
                    item { SettingSwitchRow("Zipalign", "APK nach erfolgreichem Build ausrichten", build.zipalign) { saveBuild(build.copy(zipalign = it)) } }
                    item { SettingSwitchRow("Signieren", "APK nach erfolgreichem Build signieren", build.sign) { saveBuild(build.copy(sign = it)) } }
                    item { SettingSwitchRow("Alles im Ausgabeverzeichnis", "Dekompilierte Projekte unter /apktool/output anlegen", general.decodeIntoOutputDirectory) { saveGeneral(general.copy(decodeIntoOutputDirectory = it)) } }
                    item { SettingSwitchRow("Erstellen im Ausgabeverzeichnis", "Kompilierte APKs unter /apktool/output ablegen", general.buildIntoOutputDirectory) { saveGeneral(general.copy(buildIntoOutputDirectory = it)) } }
                    item { SettingSwitchRow("Klassen*.dex dekompilieren", "Smali-Quellen beim Decode erzeugen", !decode.noSources) { saveDecode(decode.copy(noSources = !it, allSources = if (!it) false else decode.allSources, noDebugInfo = if (!it) false else decode.noDebugInfo, useRegisters = if (!it) false else decode.useRegisters)) } }
                    item { SettingSwitchRow("Analyse aller Smali", "Alle classes*.dex dekompilieren", decode.allSources, !decode.noSources) { saveDecode(decode.copy(allSources = it, noSources = if (it) false else decode.noSources)) } }
                    item { SettingSwitchRow("Ressourcen dekompilieren", "resources.arsc und XML-Ressourcen verarbeiten", !decode.noResources) { saveDecode(decode.copy(noResources = !it, onlyManifest = if (!it) false else decode.onlyManifest)) } }
                    item { SettingSwitchRow("Nur AndroidManifest.xml", "Apktool --only-manifest", decode.onlyManifest, !decode.noResources) { saveDecode(decode.copy(onlyManifest = it)) } }
                    item { SettingSwitchRow("Rohwerte ignorieren", "Apktool --ignore-raw-values", decode.ignoreRawValues, !decode.noResources) { saveDecode(decode.copy(ignoreRawValues = it)) } }
                    item {
                        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
                            Text("Ressourcen-Auflösung", style = MaterialTheme.typography.titleMedium)
                            Text("Apktool --res-resolve-mode", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            CompactPicker(decode.resourceResolveMode, ApktoolSettings.resourceResolveModes, { mode ->
                                when (mode) { "greedy" -> "Greedy"; "lazy" -> "Lazy"; else -> "Standard" }
                            }) { saveDecode(decode.copy(resourceResolveMode = it)) }
                        }
                    }
                    item { SettingSwitchRow("Assets nicht dekompilieren", "Apktool --no-assets", decode.noAssets) { saveDecode(decode.copy(noAssets = it)) } }
                    item { SettingSwitchRow("Vorhandenes Projekt überschreiben", "Apktool decode --force", decode.force) { saveDecode(decode.copy(force = it)) } }
                    item { SettingNavigationRow("Verwaltung von Rahmenwerken", "Installierte Frameworks auswählen/importieren/löschen", ApktoolSettings.frameworkLabel(framework)) { overlay = SettingsOverlay.FRAMEWORKS } }
                    item { SettingNavigationRow("Austausch von Werkzeugen", "AAPT2 auswählen oder benutzerdefiniertes AAPT2 verwenden", ApktoolSettings.aaptLabel(aapt)) { overlay = SettingsOverlay.AAPT2 } }
                    item { SettingValueRow("Ausgabeverzeichnis", "Vorgabe-Ausgabeverzeichnis", ApktoolSettings.outputRoot(context)) { outputDialog = true } }
                    item { SettingSwitchRow("aapt2 verwenden", "AAPT1 ist absichtlich nicht verfügbar", true, false) {} }
                    item { SettingSwitchRow("Debug-Informationen schreiben", "Smali-Debugdaten (.local, .param, .line)", !decode.noDebugInfo, !decode.noSources) { saveDecode(decode.copy(noDebugInfo = !it)) } }
                    item { SettingSwitchRow("apk als debuggingfähig einstellen", "android:debuggable beim Build aktivieren", build.debuggable) { saveBuild(build.copy(debuggable = it)) } }
                    item { SettingSwitchRow("Verwenden Sie \"Register\" statt \"Lokale\".", "Smali mit .registers statt .locals ausgeben", decode.useRegisters, !decode.noSources) { saveDecode(decode.copy(useRegisters = it)) } }
                    item { SettingSwitchRow("Ausführlich", "Verbose-Modus für Decode/Build", decode.verbose || build.verbose) { saveDecode(decode.copy(verbose = it)); saveBuild(build.copy(verbose = it)) } }
                    item { SettingSwitchRow("Original anpassen", "Apktool --match-original", decode.matchOriginal) { saveDecode(decode.copy(matchOriginal = it)) } }
                    item { SettingSwitchRow("Beibehaltung der Ordnerstruktur", "Ordnerstruktur soweit Apktool möglich erhalten", decode.preserveDirectoryStructure) { saveDecode(decode.copy(preserveDirectoryStructure = it)) } }
                    item { SettingSwitchRow("Hinzufügen \"APKTOOL_DUMMY\"", "Wird vom Apktool-3.x-Kern automatisch behandelt", true, false) {} }
                    item { SettingSwitchRow("Gebrochene Ressourcen beibehalten", "Apktool --keep-broken-res", decode.keepBrokenResources, !decode.noResources && !decode.onlyManifest) { saveDecode(decode.copy(keepBrokenResources = it)) } }
                    item { SettingSwitchRow("Gespaltene Spuren entfernen", "Split-Metadaten nach Decode aus dem Manifest entfernen", decode.removeSplitTraces) { saveDecode(decode.copy(removeSplitTraces = it)) } }
                    item { SettingSwitchRow("<Eigenschaft> entfernen", "<property>-Tags nach Decode entfernen", decode.removePropertyTags) { saveDecode(decode.copy(removePropertyTags = it)) } }
                    item { SettingSwitchRow("Netzwerksicherheitskonfiguration hinzufügen", "Permissive networkSecurityConfig beim Build", build.networkSecurityConfig) { saveBuild(build.copy(networkSecurityConfig = it)) } }
                    item { SettingSwitchRow("Nicht ändern, wenn sie vorhanden ist", "Vorhandene networkSecurityConfig erhalten", build.networkSecurityKeepExisting, build.networkSecurityConfig) { saveBuild(build.copy(networkSecurityKeepExisting = it)) } }
                    item { SettingNavigationRow("Signatur", "Vorgabesignatur, Keystore und v1-v4", ApktoolSettings.signatureLabel(ApktoolSettings.signatureDefaults(context))) { overlay = SettingsOverlay.SIGNATURE } }
                    item { SettingNavigationRow("Pfade & Jobs", "1-4 parallele Runner und 1-4 Apktool-Threads") { overlay = SettingsOverlay.PATHS } }
                    item { SettingNavigationRow("Runtime", "SDK 36 • AGP 8.10.1 • Gradle 8.11.1 • NDK 29.0.14033849") { overlay = SettingsOverlay.RUNTIME } }
                }
            }
        }
    }

    if (suffixDialog) {
        TextValueDialog("Suffix für apk", general.apkSuffix, "z. B. -mod", { suffixDialog = false }) {
            saveGeneral(general.copy(apkSuffix = it)); general = ApktoolSettings.generalDefaults(context); suffixDialog = false
        }
    }
    if (outputDialog) {
        TextValueDialog("Ausgabeverzeichnis", ApktoolSettings.outputRoot(context), ApktoolSettings.defaultOutputRoot(), { outputDialog = false }) {
            ApktoolSettings.savePathsAndWorkers(context, ApktoolSettings.maxWorkers(context), ApktoolSettings.projectsRoot(context), it, ApktoolSettings.apktoolThreads(context)); outputDialog = false
        }
    }

    when (overlay) {
        SettingsOverlay.FRAMEWORKS -> FrameworkManagerDialog { framework = ApktoolSettings.frameworkTag(context); overlay = SettingsOverlay.NONE }
        SettingsOverlay.AAPT2 -> Aapt2ManagerDialog { aapt = ApktoolSettings.aaptVariant(context); overlay = SettingsOverlay.NONE }
        SettingsOverlay.SIGNATURE -> SignatureManagerDialog { overlay = SettingsOverlay.NONE }
        SettingsOverlay.PATHS -> PathsAndJobsDialog { overlay = SettingsOverlay.NONE }
        SettingsOverlay.RUNTIME -> RuntimeInfoDialog { overlay = SettingsOverlay.NONE }
        SettingsOverlay.NONE -> Unit
    }
}

@Composable
private fun FrameworkManagerDialog(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var active by remember { mutableStateOf(ApktoolSettings.frameworkTag(context)) }
    var frameworks by remember { mutableStateOf<List<File>>(emptyList()) }
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
    var installPath by remember { mutableStateOf("") }
    var installTag by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }
    var refresh by remember { mutableIntStateOf(0) }

    fun load() { refresh++ }
    fun runCommand(command: String) {
        scope.launch {
            status = "Bitte warten …"
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val tc = Toolchain(context); tc.provision(); ApktoolCommandRunner(tc) { }.execute(command).summary
                }
            }
            status = result.getOrElse { it.message ?: it.toString() }
            selected = emptySet(); load()
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(uri)?.use { Toolchain(context).copyIntoInput(it, "framework.apk").absolutePath }
                        ?: error("Framework kann nicht geöffnet werden")
                }
            }
            result.onSuccess { installPath = it }.onFailure { status = it.message.orEmpty() }
        }
    }

    LaunchedEffect(refresh) {
        val result = withContext(Dispatchers.IO) { runCatching { Toolchain(context).apply { provision() }.listFrameworks() } }
        frameworks = result.getOrDefault(emptyList())
        result.exceptionOrNull()?.let { status = it.message.orEmpty() }
    }

    AlertDialog(
        onDismissRequest = onBack,
        title = { Text("Verwaltung der installierten Frameworks") },
        text = {
            Column(modifier = Modifier.heightIn(max = 600.dp)) {
                CompactPicker(active, ApktoolSettings.availableFrameworkTags(context), { ApktoolSettings.frameworkLabel(it) }) {
                    active = it; ApktoolSettings.setFrameworkTag(context, it)
                }
                LazyColumn(modifier = Modifier.heightIn(max = 250.dp)) {
                    items(frameworks, key = { it.absolutePath }) { file ->
                        val checked = file.absolutePath in selected
                        Row(modifier = Modifier.fillMaxWidth().clickable { selected = if (checked) selected - file.absolutePath else selected + file.absolutePath }, verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked, onCheckedChange = { selected = if (it) selected + file.absolutePath else selected - file.absolutePath })
                            Column(modifier = Modifier.weight(1f)) {
                                Text(file.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("%.2f MiB".format(file.length() / 1024.0 / 1024.0), style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
                TextButton(onClick = { picker.launch(arrayOf("application/vnd.android.package-archive", "application/octet-stream", "application/zip")) }) { Text("FRAMEWORK IMPORTIEREN") }
                if (installPath.isNotBlank()) {
                    OutlinedTextField(installTag, { installTag = it }, label = { Text("Tag (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    TextButton(onClick = {
                        val cmd = buildString {
                            append("apktool install-framework -p ").append(ShellTokenizer.quote(ApktoolSettings.frameworkDir()))
                            if (installTag.isNotBlank()) append(" -t ").append(ShellTokenizer.quote(installTag.trim()))
                            append(' ').append(ShellTokenizer.quote(installPath))
                        }
                        runCommand(cmd); installPath = ""
                    }) { Text("INSTALLIEREN") }
                }
                if (status.isNotBlank()) Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onBack) { Text("SCHLIESSEN") }
                TextButton(enabled = selected.isNotEmpty(), onClick = {
                    runCommand("apktool delete-frameworks " + selected.map { File(it).name }.joinToString(" ") { ShellTokenizer.quote(it) })
                }) { Text("LÖSCHEN") }
            }
        },
        confirmButton = { TextButton(onClick = { runCommand("apktool reset-frameworks") }) { Text("ZURÜCKSETZEN") } },
    )
}

@Composable
private fun Aapt2ManagerDialog(onBack: () -> Unit) {
    val context = LocalContext.current
    var selected by remember { mutableStateOf(ApktoolSettings.aaptVariant(context)) }
    var custom by remember { mutableStateOf(ApktoolSettings.customAapt2Path(context)) }
    var info by remember { mutableStateOf("") }
    var valid by remember { mutableStateOf(false) }

    LaunchedEffect(selected, custom) {
        valid = false
        val result = withContext(Dispatchers.IO) {
            runCatching {
                if (selected == "custom") {
                    val f = File(custom)
                    if (!f.isFile) error("Benutzerdefiniertes AAPT2 nicht gefunden")
                    if (AaptManager.getBinaryVersion(f) != 2) error("Datei ist kein AAPT2")
                    "${f.absolutePath}\n%.2f MiB • AAPT2 geprüft".format(f.length() / 1024.0 / 1024.0)
                } else {
                    val tc = Toolchain(context); tc.provision(); val f = tc.getAaptBinary(selected)
                    "${f.absolutePath}\n%.2f MiB • Page ${tc.runtimePageSize / 1024} KiB".format(f.length() / 1024.0 / 1024.0)
                }
            }
        }
        result.onSuccess { value -> info = value; valid = true }
            .onFailure { error -> info = error.message ?: error.toString(); valid = false }
    }

    AlertDialog(
        onDismissRequest = onBack,
        title = { Text("AAPT2 Manager") },
        text = {
            Column(modifier = Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState())) {
                Text("AAPT1 ist absichtlich nicht verfügbar.", style = MaterialTheme.typography.bodySmall)
                CompactPicker(selected, ApktoolSettings.aaptOptions, { ApktoolSettings.aaptLabel(it) }) { selected = it }
                if (selected == "custom") {
                    OutlinedTextField(custom, { custom = it }, label = { Text("AAPT2 Pfad") }, supportingText = { Text("Muss auf Android ausführbar sein") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
                Card(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    Text(info, modifier = Modifier.padding(10.dp), style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                }
            }
        },
        dismissButton = { TextButton(onClick = onBack) { Text("ABBRECHEN") } },
        confirmButton = {
            Button(enabled = valid, onClick = { ApktoolSettings.setAapt2(context, selected, custom); onBack() }) { Text("SPEICHERN") }
        },
    )
}

@Composable
fun SignatureManagerDialog(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val current = remember { ApktoolSettings.signatureDefaults(context) }
    var profile by remember { mutableStateOf(current.profile) }
    var path by remember { mutableStateOf(current.customKeystorePath) }
    var password by remember { mutableStateOf(current.customKeystorePassword) }
    var v1 by remember { mutableStateOf(current.v1) }
    var v2 by remember { mutableStateOf(current.v2) }
    var v3 by remember { mutableStateOf(current.v3) }
    var v4 by remember { mutableStateOf(current.v4) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(uri)?.use { Toolchain(context).copyIntoInput(it, "signature.jks").absolutePath }
                        ?: error("Signaturdatei kann nicht geöffnet werden")
                }
            }
            result.onSuccess { path = it; profile = "custom" }
        }
    }

    AlertDialog(
        onDismissRequest = onBack,
        title = { Text("Signatur") },
        text = {
            Column(modifier = Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState())) {
                CompactPicker(profile, ApktoolSettings.signatureProfiles, { if (it == "testkey") "Vorgabesignatur (testkey)" else "Benutzerdefinierte Signatur" }) { profile = it }
                if (profile == "custom") {
                    OutlinedTextField(path, { path = it }, label = { Text("Keystore") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    TextButton(onClick = { picker.launch(arrayOf("application/octet-stream", "application/x-pkcs12", "*/*")) }) { Text("DATEI AUSWÄHLEN") }
                    OutlinedTextField(password, { password = it }, label = { Text("Keystore-Passwort") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
                }
                SettingCheck("Signatur v1", v1) { v1 = it }
                SettingCheck("Signatur v2", v2) { v2 = it }
                SettingCheck("Signatur v3", v3) { v3 = it }
                SettingCheck("Signatur v4", v4) { v4 = it }
            }
        },
        dismissButton = { TextButton(onClick = onBack) { Text("ABBRECHEN") } },
        confirmButton = {
            Button(enabled = (v1 || v2 || v3 || v4) && (profile != "custom" || path.isNotBlank()), onClick = {
                ApktoolSettings.saveSignatureDefaults(context, ApktoolSignatureDefaults(profile, path, password, v1, v2, v3, v4)); onBack()
            }) { Text("SPEICHERN") }
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
            Column(modifier = Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState())) {
                Text("Parallele Runner: $workers", fontWeight = FontWeight.SemiBold)
                NumberPickerRow(workers) { workers = it }
                Text("Apktool Threads pro Job: $threads", fontWeight = FontWeight.SemiBold)
                NumberPickerRow(threads) { threads = it }
                OutlinedTextField(projects, { projects = it }, label = { Text("Projects root") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(output, { output = it }, label = { Text("Build output root") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
            }
        },
        dismissButton = { TextButton(onClick = onBack) { Text("ABBRECHEN") } },
        confirmButton = { Button(onClick = { ApktoolSettings.savePathsAndWorkers(context, workers, projects, output, threads); onBack() }) { Text("SPEICHERN") } },
    )
}

@Composable
private fun RuntimeInfoDialog(onBack: () -> Unit) {
    val context = LocalContext.current
    var info by remember { mutableStateOf("Lade Runtime …") }
    LaunchedEffect(Unit) {
        info = withContext(Dispatchers.IO) {
            runCatching {
                val tc = Toolchain(context); tc.provision()
                """MTApktool runtime
Apktool: ${Toolchain.VERSION}
compileSdk/targetSdk: 36
minSdk: 29
AGP: 8.10.1
Gradle: 8.11.1
NDK: 29.0.14033849
ABI: arm64-v8a
Runtime page: ${tc.runtimePageSize / 1024} KiB
Runner: ${ApktoolSettings.maxWorkers(context)}/4
Threads/job: ${ApktoolSettings.apktoolThreads(context)}/4
Root: ${tc.root.absolutePath}"""
            }.getOrElse { it.stackTraceToString() }
        }
    }
    AlertDialog(onDismissRequest = onBack, title = { Text("Runtime") }, text = { Text(info, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall) }, confirmButton = { TextButton(onClick = onBack) { Text("ZURÜCK") } })
}

@Composable
private fun TextValueDialog(title: String, value: String, hint: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by remember(value) { mutableStateOf(value) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(text, { text = it }, placeholder = { Text(hint) }, singleLine = true, modifier = Modifier.fillMaxWidth()) },
        dismissButton = { TextButton(onClick = onDismiss) { Text("ABBRECHEN") } },
        confirmButton = { TextButton(onClick = { onSave(text) }) { Text("SPEICHERN") } },
    )
}

@Composable
private fun SettingSwitchRow(title: String, subtitle: String, checked: Boolean, enabled: Boolean = true, onChecked: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(enabled = enabled) { onChecked(!checked) }.padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.5f))
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 1f else 0.55f))
        }
        Switch(checked = checked, onCheckedChange = if (enabled) onChecked else null, enabled = enabled)
    }
}

@Composable
private fun SettingNavigationRow(title: String, subtitle: String, trailing: String = "", onClick: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 12.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (trailing.isNotBlank()) Text(trailing, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun SettingValueRow(title: String, subtitle: String, value: String, onClick: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 12.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun SettingCheck(label: String, checked: Boolean, enabled: Boolean = true, onChecked: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().clickable(enabled = enabled) { onChecked(!checked) }, verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked, onCheckedChange = onChecked, enabled = enabled); Text(label)
    }
}

@Composable
private fun NumberPickerRow(value: Int, onSelected: (Int) -> Unit) {
    Row { (1..4).forEach { n -> if (n == value) Button(onClick = { onSelected(n) }) { Text("$n") } else TextButton(onClick = { onSelected(n) }) { Text("$n") } } }
}

@Composable
private fun CompactPicker(value: String, options: List<String>, label: (String) -> String, onSelected: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = Modifier.fillMaxWidth()) {
        TextButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(label(value), modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.width(8.dp)); Text("▾")
            }
        }
        DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
            options.distinct().forEach { option -> DropdownMenuItem(text = { Text(label(option)) }, onClick = { expanded = false; onSelected(option) }) }
        }
    }
}
