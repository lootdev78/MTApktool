package io.github.lootdev78.mtapktool.apktool

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
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
import io.github.apktool.android.runtime.ApktoolCommandRunner
import io.github.apktool.android.runtime.ShellTokenizer
import io.github.apktool.android.runtime.Toolchain
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class SettingsOverlay {
    NONE, FRAMEWORKS, AAPT2, SIGNATURE, PATHS, RUNTIME
}

/**
 * Full-screen Material3 settings page arranged after Apktool M's
 * "Erstellen & Dekodieren" screen. AAPT1 is intentionally absent.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApktoolSettingsDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current

    var general by remember { mutableStateOf(ApktoolSettings.generalDefaults(context)) }
    var decode by remember { mutableStateOf(ApktoolSettings.decodeDefaults(context)) }
    var build by remember { mutableStateOf(ApktoolSettings.buildDefaults(context)) }
    var framework by remember { mutableStateOf(ApktoolSettings.frameworkTag(context)) }
    var aapt by remember { mutableStateOf(ApktoolSettings.aaptVariant(context)) }
    var workers by remember { mutableIntStateOf(ApktoolSettings.maxWorkers(context)) }
    var threads by remember { mutableIntStateOf(ApktoolSettings.apktoolThreads(context)) }
    var projectsRoot by remember { mutableStateOf(ApktoolSettings.projectsRoot(context)) }
    var outputRoot by remember { mutableStateOf(ApktoolSettings.outputRoot(context)) }

    var overlay by remember { mutableStateOf(SettingsOverlay.NONE) }
    var showSuffixEditor by remember { mutableStateOf(false) }
    var showOutputEditor by remember { mutableStateOf(false) }
    var searching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var overflow by remember { mutableStateOf(false) }

    fun saveGeneral(value: ApktoolGeneralDefaults) {
        general = value
        ApktoolSettings.saveGeneralDefaults(context, value)
    }

    fun saveDecode(value: ApktoolDecodeDefaults) {
        decode = value
        ApktoolSettings.saveDecodeDefaults(context, value)
    }

    fun saveBuild(value: ApktoolBuildDefaults) {
        build = value
        ApktoolSettings.saveBuildDefaults(context, value)
    }

    fun matches(title: String, subtitle: String = ""): Boolean {
        val q = query.trim()
        return q.isEmpty() || title.contains(q, ignoreCase = true) || subtitle.contains(q, ignoreCase = true)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text("Erstellen & Dekodieren") },
                        navigationIcon = {
                            IconButton(onClick = onDismiss) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
                            }
                        },
                        actions = {
                            IconButton(onClick = {
                                searching = !searching
                                if (!searching) query = ""
                            }) {
                                Icon(Icons.Default.Search, contentDescription = "Suchen")
                            }
                            Box {
                                IconButton(onClick = { overflow = true }) {
                                    Icon(Icons.Default.MoreVert, contentDescription = "Mehr")
                                }
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
                                            workers = ApktoolSettings.maxWorkers(context)
                                            threads = ApktoolSettings.apktoolThreads(context)
                                            projectsRoot = ApktoolSettings.projectsRoot(context)
                                            outputRoot = ApktoolSettings.outputRoot(context)
                                        },
                                    )
                                }
                            }
                        },
                    )
                },
            ) { innerPadding ->
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 28.dp),
                ) {
                    if (searching) {
                        item {
                            OutlinedTextField(
                                value = query,
                                onValueChange = { query = it },
                                label = { Text("Einstellungen durchsuchen") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                            )
                        }
                    }

                    if (matches("Nach Abschluss benachrichtigen", "Erstellung und Dekompilierung")) item {
                        SettingSwitchRow(
                            title = "Nach Abschluss benachrichtigen",
                            subtitle = "Senden einer Benachrichtigung nach Abschluss der Erstellung und Dekompilierung",
                            checked = general.notifyOnCompletion,
                        ) { saveGeneral(general.copy(notifyOnCompletion = it)) }
                    }

                    if (matches("Nicht benachrichtigen, falls ausgeführt")) item {
                        SettingSwitchRow(
                            title = "Nicht benachrichtigen, falls ausgeführt",
                            subtitle = "Zeigt keine Abschlussbenachrichtigung an, wenn die App geöffnet ist",
                            checked = general.suppressCompletionWhileOpen,
                            enabled = general.notifyOnCompletion,
                        ) { saveGeneral(general.copy(suppressCompletionWhileOpen = it)) }
                    }

                    if (matches("Suffix für apk")) item {
                        SettingValueRow(
                            title = "Suffix für apk",
                            subtitle = "Suffix für den Namen der Ausgabedatei",
                            value = general.apkSuffix.ifBlank { "Kein Suffix" },
                            onClick = { showSuffixEditor = true },
                        )
                    }

                    if (matches("Ordner build löschen")) item {
                        SettingSwitchRow(
                            title = "Ordner \"build\" löschen",
                            subtitle = "Löschen des Ordners build nach der Kompilierung",
                            checked = build.deleteBuildDirectory,
                        ) { saveBuild(build.copy(deleteBuildDirectory = it)) }
                    }

                    if (matches("Alles im Ausgabeverzeichnis")) item {
                        SettingSwitchRow(
                            title = "Alles im Ausgabeverzeichnis",
                            subtitle = "Alles in das Ausgabeverzeichnis dekompilieren",
                            checked = general.decodeIntoOutputDirectory,
                        ) { saveGeneral(general.copy(decodeIntoOutputDirectory = it)) }
                    }

                    if (matches("Erstellen im Ausgabeverzeichnis")) item {
                        SettingSwitchRow(
                            title = "Erstellen im Ausgabeverzeichnis",
                            subtitle = "Bei der Erstellung von Projekten werden die APKs im Ausgabeverzeichnis abgelegt",
                            checked = general.buildIntoOutputDirectory,
                        ) { saveGeneral(general.copy(buildIntoOutputDirectory = it)) }
                    }

                    if (matches("Analyse aller Smali")) item {
                        SettingSwitchRow(
                            title = "Analyse aller Smali",
                            subtitle = "Alle DEX/Smali-Klassen beim Dekompilieren einbeziehen",
                            checked = decode.allSources,
                        ) { saveDecode(decode.copy(allSources = it, noSources = if (it) false else decode.noSources)) }
                    }

                    if (matches("Benutzerdefinierter Rahmen")) item {
                        SettingSwitchRow(
                            title = "Benutzerdefinierter Rahmen",
                            subtitle = "Verwendung eines vom Benutzer importierten Frameworks anstelle des integrierten Frameworks",
                            checked = !ApktoolSettings.isBuiltInFramework(framework),
                        ) { checked ->
                            if (checked) {
                                overlay = SettingsOverlay.FRAMEWORKS
                            } else {
                                framework = ApktoolSettings.DEFAULT_FRAMEWORK
                                ApktoolSettings.setFrameworkTag(context, framework)
                            }
                        }
                    }

                    if (matches("Austausch von Werkzeugen")) item {
                        SettingNavigationRow(
                            title = "Austausch von Werkzeugen",
                            subtitle = "Auswahl und Ersatz von aapt2",
                            trailing = ApktoolSettings.aaptLabel(aapt),
                            onClick = { overlay = SettingsOverlay.AAPT2 },
                        )
                    }

                    if (matches("Verwaltung von Rahmenwerken")) item {
                        SettingNavigationRow(
                            title = "Verwaltung von Rahmenwerken",
                            subtitle = "Verwaltung der installierten Frameworks",
                            trailing = ApktoolSettings.frameworkLabel(framework),
                            onClick = { overlay = SettingsOverlay.FRAMEWORKS },
                        )
                    }

                    if (matches("Ausgabeverzeichnis")) item {
                        SettingValueRow(
                            title = "Ausgabeverzeichnis",
                            subtitle = "Das Vorgabe-Ausgabeverzeichnis festlegen",
                            value = outputRoot,
                            onClick = { showOutputEditor = true },
                        )
                    }

                    if (matches("aapt2 verwenden")) item {
                        SettingSwitchRow(
                            title = "aapt2 verwenden",
                            subtitle = "AAPT1 ist in MTApktool nicht verfügbar • ${ApktoolSettings.aaptLabel(aapt)}",
                            checked = true,
                            onChecked = { overlay = SettingsOverlay.AAPT2 },
                        )
                    }

                    if (matches("Debug-Informationen schreiben")) item {
                        SettingSwitchRow(
                            title = "Debug-Informationen schreiben",
                            subtitle = "Debug-Informationen ausgeben (.local, .param, .line, etc.)",
                            checked = !decode.noDebugInfo,
                        ) { saveDecode(decode.copy(noDebugInfo = !it)) }
                    }

                    if (matches("apk als debuggingfähig einstellen")) item {
                        SettingSwitchRow(
                            title = "apk als debuggingfähig einstellen",
                            subtitle = "Setzt android:debuggable auf true im kompilierten Manifest der APK",
                            checked = build.debuggable,
                        ) { saveBuild(build.copy(debuggable = it)) }
                    }

                    if (matches("Register statt Lokale")) item {
                        SettingSwitchRow(
                            title = "Verwenden Sie \"Register\" statt \"Lokale\".",
                            subtitle = "Bei der Dekompilierung nach Smali .registers anstelle von .locals verwenden",
                            checked = decode.useRegisters,
                        ) { saveDecode(decode.copy(useRegisters = it)) }
                    }

                    if (matches("Ausführlich")) item {
                        SettingSwitchRow(
                            title = "Ausführlich",
                            subtitle = "Ausführlichen Modus für Dekompilierung und Build einschalten",
                            checked = decode.verbose || build.verbose,
                        ) {
                            saveDecode(decode.copy(verbose = it))
                            saveBuild(build.copy(verbose = it))
                        }
                    }

                    if (matches("Original anpassen")) item {
                        SettingSwitchRow(
                            title = "Original anpassen",
                            subtitle = "Die Originalsignatur und das Manifest werden soweit Apktool dies unterstützt aufbewahrt",
                            checked = decode.matchOriginal,
                        ) { saveDecode(decode.copy(matchOriginal = it)) }
                    }

                    if (matches("Beibehaltung der Ordnerstruktur")) item {
                        SettingSwitchRow(
                            title = "Beibehaltung der Ordnerstruktur",
                            subtitle = "Versuche, die Ordnerstruktur des Originals beizubehalten; nützlich für Systemanwendungen",
                            checked = decode.preserveDirectoryStructure,
                        ) { saveDecode(decode.copy(preserveDirectoryStructure = it)) }
                    }

                    if (matches("APKTOOL_DUMMY")) item {
                        SettingSwitchRow(
                            title = "Hinzufügen \"APKTOOL_DUMMY\"",
                            subtitle = "Fehlende Ressourcen werden von Apktool 3.x automatisch als APKTOOL_DUMMY behandelt",
                            checked = true,
                            enabled = false,
                            onChecked = {},
                        )
                    }

                    if (matches("Gebrochene Ressourcen beibehalten")) item {
                        SettingSwitchRow(
                            title = "Gebrochene Ressourcen beibehalten",
                            subtitle = "Ressourcen trotz Dekompilierfehlern soweit möglich behalten",
                            checked = decode.keepBrokenResources,
                        ) { saveDecode(decode.copy(keepBrokenResources = it)) }
                    }

                    if (matches("Gespaltene Spuren entfernen")) item {
                        SettingSwitchRow(
                            title = "Gespaltene Spuren entfernen",
                            subtitle = "Entfernen von Split-Spuren aus Ressourcen während der Dekompilierung",
                            checked = decode.removeSplitTraces,
                        ) { saveDecode(decode.copy(removeSplitTraces = it)) }
                    }

                    if (matches("Eigenschaft entfernen")) item {
                        SettingSwitchRow(
                            title = "<Eigenschaft> entfernen",
                            subtitle = "Entfernen von <property>-Tags aus dem Manifest beim Dekompilieren",
                            checked = decode.removePropertyTags,
                        ) { saveDecode(decode.copy(removePropertyTags = it)) }
                    }

                    if (matches("Pakete zusammenzuführen")) item {
                        SettingSwitchRow(
                            title = "Versuche, Pakete zusammenzuführen",
                            subtitle = "Greedy-Ressourcenauflösung für zusätzliche Ressourcenpakete verwenden",
                            checked = decode.resourceResolveMode == "greedy",
                        ) {
                            saveDecode(decode.copy(resourceResolveMode = if (it) "greedy" else "default"))
                        }
                    }

                    if (matches("Beschreibungen für Berechtigungen")) item {
                        SettingSwitchRow(
                            title = "Beschreibungen für Berechtigungen",
                            subtitle = "Vom aktuellen Apktool-A Kern nicht direkt unterstützt",
                            checked = false,
                            enabled = false,
                            onChecked = {},
                        )
                    }

                    if (matches("Netzwerksicherheitskonfiguration hinzufügen")) item {
                        SettingSwitchRow(
                            title = "Netzwerksicherheitskonfiguration hinzufügen",
                            subtitle = "Eine allgemein zulässige Netzwerksicherheitskonfiguration zur Erstellungszeit hinzufügen",
                            checked = build.networkSecurityConfig,
                        ) { saveBuild(build.copy(networkSecurityConfig = it)) }
                    }

                    if (matches("Nicht ändern, wenn sie vorhanden ist")) item {
                        SettingSwitchRow(
                            title = "Nicht ändern, wenn sie vorhanden ist",
                            subtitle = "Eine bereits vorhandene Netzwerksicherheitskonfiguration nicht überschreiben",
                            checked = build.networkSecurityKeepExisting,
                            enabled = build.networkSecurityConfig,
                        ) { saveBuild(build.copy(networkSecurityKeepExisting = it)) }
                    }

                    if (matches("Benachrichtigung am Arbeitsplatz")) item {
                        SettingSwitchRow(
                            title = "Benachrichtigung am Arbeitsplatz",
                            subtitle = "Während laufender Jobs erforderlich, da Android den Foreground-Service sichtbar halten muss",
                            checked = true,
                            enabled = false,
                            onChecked = {},
                        )
                    }

                    if (matches("Signatur")) item {
                        SettingNavigationRow(
                            title = "Signatur",
                            subtitle = "Vorgabesignatur, benutzerdefinierter Keystore und v1–v4",
                            trailing = ApktoolSettings.signatureLabel(ApktoolSettings.signatureDefaults(context)),
                            onClick = { overlay = SettingsOverlay.SIGNATURE },
                        )
                    }

                    if (matches("Pfade & Jobs")) item {
                        SettingNavigationRow(
                            title = "Pfade & Jobs",
                            subtitle = "Projekte, Ausgabe, Apktool-Threads und parallele Runner",
                            trailing = "$workers Runner • $threads Threads",
                            onClick = { overlay = SettingsOverlay.PATHS },
                        )
                    }

                    if (matches("Runtime")) item {
                        SettingNavigationRow(
                            title = "Runtime",
                            subtitle = "SDK 36 • NDK 29.0.14033849 • arm64-v8a",
                            onClick = { overlay = SettingsOverlay.RUNTIME },
                        )
                    }
                }
            }
        }
    }

    if (showSuffixEditor) {
        TextValueDialog(
            title = "Suffix für apk",
            value = general.apkSuffix,
            hint = "z. B. -mod",
            onDismiss = { showSuffixEditor = false },
            onSave = {
                saveGeneral(general.copy(apkSuffix = it))
                general = ApktoolSettings.generalDefaults(context)
                showSuffixEditor = false
            },
        )
    }

    if (showOutputEditor) {
        ApktoolFolderPickerDialog(
            initialPath = outputRoot,
            title = "Ausgabeverzeichnis",
            onDismiss = { showOutputEditor = false },
            onSelected = {
                outputRoot = it.ifBlank { ApktoolSettings.defaultOutputRoot() }
                ApktoolSettings.savePathsAndWorkers(context, workers, projectsRoot, outputRoot, threads)
                showOutputEditor = false
            },
        )
    }

    when (overlay) {
        SettingsOverlay.FRAMEWORKS -> FrameworkManagerDialog(onBack = {
            framework = ApktoolSettings.frameworkTag(context)
            overlay = SettingsOverlay.NONE
        })

        SettingsOverlay.AAPT2 -> Aapt2ManagerDialog(onBack = {
            aapt = ApktoolSettings.aaptVariant(context)
            overlay = SettingsOverlay.NONE
        })

        SettingsOverlay.SIGNATURE -> SignatureManagerDialog(onBack = { overlay = SettingsOverlay.NONE })

        SettingsOverlay.PATHS -> PathsAndJobsDialog(onBack = {
            workers = ApktoolSettings.maxWorkers(context)
            threads = ApktoolSettings.apktoolThreads(context)
            projectsRoot = ApktoolSettings.projectsRoot(context)
            outputRoot = ApktoolSettings.outputRoot(context)
            overlay = SettingsOverlay.NONE
        })

        SettingsOverlay.RUNTIME -> RuntimeInfoDialog(onBack = { overlay = SettingsOverlay.NONE })
        SettingsOverlay.NONE -> Unit
    }
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
                result.onSuccess {
                    installPath = it
                    status = "Framework ausgewählt."
                }.onFailure {
                    status = it.message ?: it.toString()
                }
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
                Text("Aktives Framework", style = MaterialTheme.typography.labelLarge)
                CompactPicker(
                    value = active,
                    options = ApktoolSettings.availableFrameworkTags(context),
                    label = { ApktoolSettings.frameworkLabel(it) },
                    onSelected = {
                        active = it
                        ApktoolSettings.setFrameworkTag(context, it)
                    },
                )

                Spacer(Modifier.height(6.dp))
                if (frameworks.isEmpty()) {
                    Text("Keine Framework-Dateien gefunden.", style = MaterialTheme.typography.bodySmall)
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 230.dp)) {
                        items(frameworks, key = { it.absolutePath }) { file ->
                            val checked = file.absolutePath in selected
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selected = if (checked) selected - file.absolutePath else selected + file.absolutePath
                                    },
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(
                                    checked = checked,
                                    onCheckedChange = {
                                        selected = if (it) selected + file.absolutePath else selected - file.absolutePath
                                    },
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(file.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
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

                TextButton(
                    onClick = {
                        frameworkPicker.launch(
                            arrayOf(
                                "application/vnd.android.package-archive",
                                "application/octet-stream",
                                "application/zip",
                            ),
                        )
                    },
                ) { Text("FRAMEWORK IMPORTIEREN") }

                if (installPath.isNotBlank()) {
                    OutlinedTextField(
                        value = installTag,
                        onValueChange = { installTag = it },
                        label = { Text("Framework-Tag (optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    TextButton(
                        onClick = {
                            val command = buildString {
                                append("apktool install-framework -p ")
                                    .append(ShellTokenizer.quote(ApktoolSettings.frameworkDir()))
                                if (installTag.isNotBlank()) {
                                    append(" -t ").append(ShellTokenizer.quote(installTag.trim()))
                                }
                                append(' ').append(ShellTokenizer.quote(installPath))
                            }
                            runFrameworkCommand(command)
                            installPath = ""
                        },
                    ) { Text("INSTALLIEREN") }
                }

                if (status.isNotBlank()) {
                    Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
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
fun Aapt2ManagerDialog(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selected by remember { mutableStateOf(ApktoolSettings.aaptVariant(context)) }
    var customPath by remember { mutableStateOf(ApktoolSettings.customAapt2Path(context)) }
    var runtimeInfo by remember { mutableStateOf("") }

    val aaptPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                val result = withContext(Dispatchers.IO) {
                    runCatching {
                        val toolchain = Toolchain(context)
                        toolchain.provision()
                        val file = context.contentResolver.openInputStream(uri)?.use { input ->
                            toolchain.copyIntoInput(input, "custom-aapt2")
                        } ?: error("AAPT2-Datei kann nicht geöffnet werden")
                        if (!file.setExecutable(true, false) && !file.canExecute()) {
                            error("AAPT2-Datei ist nicht ausführbar")
                        }
                        file.absolutePath
                    }
                }
                result.onSuccess {
                    customPath = it
                    selected = "custom"
                }
            }
        }
    }

    LaunchedEffect(selected, customPath) {
        runtimeInfo = withContext(Dispatchers.IO) {
            runCatching {
                if (selected == "custom") {
                    val file = File(customPath)
                    if (!file.isFile) {
                        "Benutzerdefiniertes AAPT2 nicht gefunden"
                    } else {
                        "${file.absolutePath}\n%.2f MiB".format(file.length() / 1024.0 / 1024.0)
                    }
                } else {
                    val toolchain = Toolchain(context)
                    toolchain.provision()
                    val file = toolchain.getAaptBinary(selected)
                    "${file.absolutePath}\n%.2f MiB • Page ${toolchain.runtimePageSize / 1024} KiB".format(
                        file.length() / 1024.0 / 1024.0,
                    )
                }
            }.getOrElse { it.message ?: it.toString() }
        }
    }

    AlertDialog(
        onDismissRequest = onBack,
        title = { Text("AAPT2 Manager") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 560.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    "AAPT1 ist absichtlich nicht verfügbar.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                    TextButton(onClick = { aaptPicker.launch(arrayOf("application/octet-stream", "*/*")) }) {
                        Text("DATEI AUSWÄHLEN")
                    }
                }

                Card(modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
                    Column(Modifier.padding(10.dp)) {
                        Text("Aktive Binary", fontWeight = FontWeight.SemiBold)
                        Text(
                            runtimeInfo,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
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
                result.onSuccess {
                    path = it
                    profile = "custom"
                    status = "Signaturdatei ausgewählt."
                }.onFailure {
                    status = it.message ?: it.toString()
                }
            }
        }
    }

    AlertDialog(
        onDismissRequest = onBack,
        title = { Text("Signatur") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 570.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
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
                    TextButton(onClick = { picker.launch(arrayOf("application/octet-stream", "application/x-pkcs12", "*/*")) }) {
                        Text("DATEI AUSWÄHLEN")
                    }
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

                if (status.isNotBlank()) {
                    Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        dismissButton = { TextButton(onClick = onBack) { Text("ABBRECHEN") } },
        confirmButton = {
            Button(
                enabled = (v1 || v2 || v3 || v4) && (profile != "custom" || path.isNotBlank()),
                onClick = {
                    ApktoolSettings.saveSignatureDefaults(
                        context,
                        ApktoolSignatureDefaults(profile, path, password, v1, v2, v3, v4),
                    )
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
    var showProjectsPicker by remember { mutableStateOf(false) }
    var showOutputPicker by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onBack,
        title = { Text("Pfade & Jobs") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 570.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text("Parallele Runner", fontWeight = FontWeight.SemiBold)
                NumberPickerRow(value = workers, range = 1..4) { workers = it }
                SettingHint("Maximal vier Decode/Build-Jobs gleichzeitig. Jeder Job kann einzeln gestoppt werden.")

                Text("Apktool Threads pro Job", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 6.dp))
                NumberPickerRow(value = threads, range = 1..4) { threads = it }
                SettingHint("Apktool -j/--jobs. Der Android-Port begrenzt pro Job auf 1–4 Threads.")

                OutlinedTextField(
                    value = projects,
                    onValueChange = { projects = it },
                    label = { Text("Projects root") },
                    supportingText = { Text("Standard: ${ApktoolSettings.defaultProjectsRoot()}") },
                    singleLine = true,
                    trailingIcon = {
                        IconButton(onClick = { showProjectsPicker = true }) {
                            Icon(Icons.Default.FolderOpen, contentDescription = "Projects root auswählen")
                        }
                    },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                OutlinedTextField(
                    value = output,
                    onValueChange = { output = it },
                    label = { Text("Build output root") },
                    supportingText = { Text("Standard: ${ApktoolSettings.defaultOutputRoot()}") },
                    singleLine = true,
                    trailingIcon = {
                        IconButton(onClick = { showOutputPicker = true }) {
                            Icon(Icons.Default.FolderOpen, contentDescription = "Build output root auswählen")
                        }
                    },
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

    if (showProjectsPicker) {
        ApktoolFolderPickerDialog(
            initialPath = projects,
            title = "Projects root",
            onDismiss = { showProjectsPicker = false },
            onSelected = {
                projects = it
                showProjectsPicker = false
            },
        )
    }
    if (showOutputPicker) {
        ApktoolFolderPickerDialog(
            initialPath = output,
            title = "Build output root",
            onDismiss = { showOutputPicker = false },
            onSelected = {
                output = it
                showOutputPicker = false
            },
        )
    }
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
                    append("minSdk: 29\n")
                    append("AGP: 8.10.1\n")
                    append("Gradle: 8.11.1\n")
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
private fun TextValueDialog(
    title: String,
    value: String,
    hint: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var text by remember(value) { mutableStateOf(value) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text(hint) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("ABBRECHEN") } },
        confirmButton = { TextButton(onClick = { onSave(text) }) { Text("SPEICHERN") } },
    )
}

@Composable
private fun SettingSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean = true,
    onChecked: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onChecked(!checked) }
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.5f),
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 1f else 0.55f),
            )
        }
        Switch(checked = checked, onCheckedChange = if (enabled) onChecked else null, enabled = enabled)
    }
}

@Composable
private fun SettingNavigationRow(
    title: String,
    subtitle: String,
    trailing: String = "",
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (trailing.isNotBlank()) {
                Text(
                    trailing,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}

@Composable
private fun SettingValueRow(
    title: String,
    subtitle: String,
    value: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                value,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

@Composable
private fun SettingCheck(
    label: String,
    checked: Boolean,
    enabled: Boolean = true,
    onChecked: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onChecked(!checked) },
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
        modifier = Modifier.padding(start = 4.dp, bottom = 5.dp),
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
private fun CompactPicker(
    value: String,
    options: List<String>,
    label: (String) -> String,
    onSelected: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = Modifier.fillMaxWidth()) {
        TextButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(label(value), modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.width(8.dp))
                Text("▾")
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.distinct().forEach { option ->
                DropdownMenuItem(
                    text = { Text(label(option)) },
                    onClick = {
                        expanded = false
                        onSelected(option)
                    },
                )
            }
        }
    }
}

private fun frameworkSdkLabel(file: File): String {
    val sdk = Regex("sdk(\\d+)", RegexOption.IGNORE_CASE).find(file.name)?.groupValues?.getOrNull(1)
    return when {
        sdk != null -> "SDK $sdk"
        file.name.matches(Regex("\\d+(-.+)?\\.apk", RegexOption.IGNORE_CASE)) -> "Framework"
        else -> "Benutzerdefiniert"
    }
}
