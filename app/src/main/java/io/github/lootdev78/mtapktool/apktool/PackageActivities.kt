package io.github.lootdev78.mtapktool.apktool

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.InstallMobile
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import io.github.lootdev78.mtapktool.core.theme.MTExplorerTheme
import io.github.lootdev78.mtapktool.feature.explorer.util.ApkArchiveInfo
import io.github.lootdev78.mtapktool.feature.explorer.util.ApkArchiveReader
import io.github.lootdev78.mtapktool.feature.explorer.util.sdkLabel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.DateFormat
import java.util.Date
import java.util.Locale

class ApkInfoActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val file = intent.getStringExtra(EXTRA_PATH)?.let(::File)?.takeIf { it.isFile } ?: run { finish(); return }
        val leftPath = intent.getStringExtra(EXTRA_LEFT_PATH)
        val rightPath = intent.getStringExtra(EXTRA_RIGHT_PATH)
        val sourcePane = intent.getStringExtra(EXTRA_SOURCE_PANE)
        setContent {
            MTExplorerTheme {
                ApkInfoPage(
                    file = file,
                    leftPath = leftPath,
                    rightPath = rightPath,
                    sourcePane = sourcePane,
                    onBack = ::finish,
                    onView = {
                        setResult(RESULT_OK, Intent().putExtra(RESULT_ACTION, ACTION_VIEW_ARCHIVE).putExtra(EXTRA_PATH, file.absolutePath))
                        finish()
                    },
                )
            }
        }
    }

    companion object {
        const val EXTRA_PATH = "path"
        const val EXTRA_LEFT_PATH = "left_path"
        const val EXTRA_RIGHT_PATH = "right_path"
        const val EXTRA_SOURCE_PANE = "source_pane"
        const val RESULT_ACTION = "result_action"
        const val ACTION_VIEW_ARCHIVE = "view_archive"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ApkInfoPage(
    file: File,
    leftPath: String?,
    rightPath: String?,
    sourcePane: String?,
    onBack: () -> Unit,
    onView: () -> Unit,
) {
    val context = LocalContext.current
    val info by produceState<ApkArchiveInfo?>(initialValue = null, file.absolutePath, file.lastModified()) {
        value = withContext(Dispatchers.IO) { ApkArchiveReader.read(context, file) }
    }
    var functions by remember { mutableStateOf(false) }
    var decode by remember { mutableStateOf(false) }
    var framework by remember { mutableStateOf(false) }
    var zipalign by remember { mutableStateOf(false) }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("APK-Information") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück") } },
        )
    }) { insets ->
        if (info == null) {
            androidx.compose.foundation.layout.Box(Modifier.fillMaxSize().padding(insets), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else {
            Column(Modifier.fillMaxSize().padding(insets)) {
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (info!!.icon != null) Image(info!!.icon!!.asImageBitmap(), null, Modifier.size(82.dp)) else Icon(Icons.Default.Android, null, Modifier.size(82.dp))
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text(info!!.label, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                            Text(info!!.versionName, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    ApkRow("Paketname", info!!.packageName)
                    ApkRow("Versionscode", info!!.versionCode.toString())
                    ApkRow("Dateigröße", formatSize(file.length()))
                    if (!sourcePane.isNullOrBlank()) ApkRow("Panel", if (sourcePane == "LEFT") "Links" else "Rechts")
                    ApkRow("Signatur", info!!.signatureSchemes)
                    ApkRow("Schutz", info!!.protection)
                    ApkRow("Target SDK", sdkLabel(info!!.targetSdk))
                    ApkRow("Minimum SDK", sdkLabel(info!!.minSdk))
                    ApkRow("Installiert", info!!.installedVersion ?: "Nicht installiert")
                    info!!.installedDataDir?.let { ApkRow("Datenordner 1", it) }
                    info!!.externalDataDir?.let { ApkRow("Datenordner 2", it) }
                    info!!.installedApkPath?.let { ApkRow("APK-Pfad", it) }
                    info!!.firstInstallTime?.let { ApkRow("Erste Installation", DateFormat.getDateTimeInstance().format(Date(it))) }
                    info!!.lastUpdateTime?.let { ApkRow("Letztes Update", DateFormat.getDateTimeInstance().format(Date(it))) }
                    info!!.installedUid?.let { ApkRow("UID", it.toString()) }
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                    TextButton(onClick = { functions = true }) { Text("FUNKTIONEN") }
                    TextButton(onClick = onView) { Text("ANZEIGEN") }
                    TextButton(onClick = { installSingleApk(context, file) }) { Text("INSTALLIEREN") }
                }
            }
        }
    }

    if (functions) {
        AlertDialog(
            onDismissRequest = { functions = false },
            title = { Text("Funktionen") },
            text = {
                Column {
                    FunctionRow(Icons.Default.Build, "Dekompilieren") { functions = false; decode = true }
                    FunctionRow(Icons.Default.FolderOpen, "Als Framework importieren") { functions = false; framework = true }
                    FunctionRow(Icons.Default.Tune, "Zipalign") { functions = false; zipalign = true }
                    FunctionRow(Icons.Default.Share, "Teilen") { share(context, file) }
                }
            },
            confirmButton = { TextButton(onClick = { functions = false }) { Text("SCHLIESSEN") } },
        )
    }
    if (decode) ApktoolDecodeDialog(
        file = file,
        leftPanelPath = leftPath,
        rightPanelPath = rightPath,
        onDismiss = { decode = false },
    )
    if (framework) ApktoolFrameworkImportDialog(file, onDismiss = { framework = false })
    if (zipalign) ZipalignDialog(file, onDismiss = { zipalign = false })
}

@Composable
private fun ApkRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.Top) {
        Text(label, modifier = Modifier.width(135.dp), fontWeight = FontWeight.Medium)
        Text(value, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun FunctionRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null); Spacer(Modifier.width(14.dp)); Text(label, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun ZipalignDialog(file: File, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val defaults = remember(context) { ApkModulePreferences.load(context) }
    var output by remember(file.absolutePath) { mutableStateOf(File(file.parentFile, file.nameWithoutExtension + "-aligned.apk").absolutePath) }
    var alignment by remember { mutableStateOf(defaults.zipAlignment.toString()) }
    var soAlignment by remember { mutableStateOf(defaults.sharedLibraryAlignment.toString()) }
    var verifyOnly by remember { mutableStateOf(false) }
    var force by remember { mutableStateOf(defaults.zipForce) }
    var verifyAfter by remember { mutableStateOf(defaults.zipVerify) }
    var status by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Zipalign") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(output, { output = it }, label = { Text("Ausgabe") }, singleLine = true, modifier = Modifier.fillMaxWidth(), enabled = !verifyOnly)
                OutlinedTextField(alignment, { alignment = it.filter(Char::isDigit) }, label = { Text("Alignment") }, supportingText = { Text("Muss eine Zweierpotenz sein, z. B. 4") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(soAlignment, { soAlignment = it.filter(Char::isDigit) }, label = { Text(".so Page Alignment") }, supportingText = { Text("0 deaktiviert Shared-Library-Alignment") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(Modifier.fillMaxWidth().clickable { verifyOnly = !verifyOnly }, verticalAlignment = Alignment.CenterVertically) { Checkbox(verifyOnly, { verifyOnly = it }); Text("Nur prüfen") }
                Row(Modifier.fillMaxWidth().clickable(enabled = !verifyOnly) { force = !force }, verticalAlignment = Alignment.CenterVertically) { Checkbox(force, { force = it }, enabled = !verifyOnly); Text("Vorhandene Ausgabe überschreiben") }
                Row(Modifier.fillMaxWidth().clickable(enabled = !verifyOnly) { verifyAfter = !verifyAfter }, verticalAlignment = Alignment.CenterVertically) { Checkbox(verifyAfter, { verifyAfter = it }, enabled = !verifyOnly); Text("Nach dem Ausrichten verifizieren") }
                if (status.isNotBlank()) Text(status, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("ABBRECHEN") } },
        confirmButton = {
            TextButton(onClick = {
                val a = alignment.toIntOrNull() ?: 4
                val so = soAlignment.toIntOrNull() ?: 16384
                ApkModulePreferences.save(context, defaults.copy(zipAlignment = a, sharedLibraryAlignment = so, zipForce = force, zipVerify = verifyAfter))
                scope.launch {
                    val result = withContext(Dispatchers.IO) {
                        runCatching {
                            if (verifyOnly) {
                                SplitPackageTools.zipAlign(file, File(output), a, so, force, true)
                            } else {
                                val ok = SplitPackageTools.zipAlign(file, File(output), a, so, force, false)
                                if (!ok) throw java.io.IOException("zipalign fehlgeschlagen")
                                if (verifyAfter && !SplitPackageTools.zipAlign(File(output), File(output), a, so, force, true)) {
                                    throw java.io.IOException("Verifikation der Ausrichtung fehlgeschlagen")
                                }
                                true
                            }
                        }
                    }
                    status = result.fold(
                        { if (verifyOnly) if (it) "Ausrichtung gültig" else "Nicht ausgerichtet" else "Fertig: $output" },
                        { it.message ?: it.toString() },
                    )
                }
            }) { Text(if (verifyOnly) "PRÜFEN" else "START") }
        },
    )
}

class SplitPackageActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val file = intent.getStringExtra(EXTRA_PATH)?.let(::File)?.takeIf(SplitArchiveSupport::isSplitArchive) ?: run { finish(); return }
        val leftPath = intent.getStringExtra(EXTRA_LEFT_PATH)
        val rightPath = intent.getStringExtra(EXTRA_RIGHT_PATH)
        val sourcePane = intent.getStringExtra(EXTRA_SOURCE_PANE)
        setContent { MTExplorerTheme { SplitPackagePage(file, leftPath, rightPath, sourcePane, ::finish) } }
    }
    companion object {
        const val EXTRA_PATH = "path"
        const val EXTRA_LEFT_PATH = "left_path"
        const val EXTRA_RIGHT_PATH = "right_path"
        const val EXTRA_SOURCE_PANE = "source_pane"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SplitPackagePage(
    file: File,
    leftPath: String?,
    rightPath: String?,
    sourcePane: String?,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val entries by produceState(initialValue = emptyList<SplitArchiveSupport.ApkEntry>(), file.absolutePath, file.lastModified()) {
        value = withContext(Dispatchers.IO) { runCatching { SplitArchiveSupport.inspect(file) }.getOrDefault(emptyList()) }
    }
    val moduleDefaults = remember(context) { ApkModulePreferences.load(context) }
    var selectedPaths by remember(file.absolutePath) { mutableStateOf<Set<String>>(emptySet()) }
    var decode by remember { mutableStateOf(false) }
    var convertDialog by remember { mutableStateOf(false) }
    var running by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }

    fun mandatoryPath(): String? = entries.firstOrNull { it.preferred }?.path
    fun normalizeSelection(paths: Set<String>): Set<String> = buildSet {
        addAll(paths)
        mandatoryPath()?.let(::add)
    }
    fun runTask(block: suspend () -> String) {
        if (running) return
        scope.launch {
            running = true
            status = runCatching { block() }.fold({ it }, { it.message ?: it.toString() })
            running = false
        }
    }

    LaunchedEffect(entries) {
        if (entries.isNotEmpty() && selectedPaths.isEmpty()) {
            selectedPaths = normalizeSelection(
                if (moduleDefaults.autoSelectDeviceSplits) {
                    SplitPackageTools.selectForDevice(context, entries, moduleDefaults.includeFeatureSplits)
                } else entries.mapTo(linkedSetOf()) { it.path },
            )
        }
    }

    Scaffold(topBar = {
        TopAppBar(
            title = {
                Column {
                    Text(file.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${file.extension.uppercase()} • ${entries.size} APKs • ${selectedPaths.size} gewählt", style = MaterialTheme.typography.labelSmall)
                }
            },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück") } },
        )
    }) { insets ->
        Column(Modifier.fillMaxSize().padding(insets)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                Button(
                    enabled = !running && selectedPaths.isNotEmpty(),
                    onClick = { runTask { withContext(Dispatchers.IO) { SplitPackageTools.install(context, file, selectedPaths) }; "Installation gestartet (${selectedPaths.size} APKs)" } },
                ) { Icon(Icons.Default.InstallMobile, null); Spacer(Modifier.width(5.dp)); Text("INSTALL") }
                Button(enabled = !running && selectedPaths.isNotEmpty(), onClick = { convertDialog = true }) {
                    Icon(Icons.Default.Tune, null); Spacer(Modifier.width(5.dp)); Text("ZU APK")
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                TextButton(enabled = entries.isNotEmpty(), onClick = {
                    selectedPaths = normalizeSelection(SplitPackageTools.selectForDevice(context, entries, moduleDefaults.includeFeatureSplits))
                }) { Text("AUTO") }
                TextButton(enabled = entries.isNotEmpty(), onClick = { selectedPaths = entries.mapTo(linkedSetOf()) { it.path } }) { Text("ALLE") }
                TextButton(enabled = entries.isNotEmpty(), onClick = { selectedPaths = mandatoryPath()?.let(::setOf).orEmpty() }) { Text("NUR BASE") }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                TextButton(enabled = !running && entries.isNotEmpty(), onClick = {
                    runTask { "Base extrahiert: " + withContext(Dispatchers.IO) { SplitPackageTools.extractPreferred(file).absolutePath } }
                }) { Text("BASE EXTRAHIEREN") }
                TextButton(enabled = !running && selectedPaths.isNotEmpty(), onClick = {
                    runTask { val files = withContext(Dispatchers.IO) { SplitPackageTools.extractSelected(file, selectedPaths) }; "${files.size} APKs extrahiert" }
                }) { Text("AUSWAHL EXTRAHIEREN") }
                TextButton(enabled = !running, onClick = { decode = true }) { Text("DEKOMPILIEREN") }
            }
            if (running) Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) { CircularProgressIndicator(Modifier.size(24.dp)); Spacer(Modifier.width(10.dp)); Text("Verarbeitung …") }
            if (status.isNotBlank()) Text(status, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            LazyColumn(Modifier.weight(1f)) {
                items(entries, key = { it.path }) { entry ->
                    val checked = entry.path in selectedPaths
                    Row(
                        Modifier.fillMaxWidth().clickable {
                            selectedPaths = if (entry.preferred) normalizeSelection(selectedPaths) else normalizeSelection(
                                if (checked) selectedPaths - entry.path else selectedPaths + entry.path,
                            )
                        }.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = checked,
                            onCheckedChange = if (entry.preferred) null else { value ->
                                selectedPaths = normalizeSelection(if (value) selectedPaths + entry.path else selectedPaths - entry.path)
                            },
                        )
                        Icon(Icons.Default.Archive, null)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(entry.displayName, fontWeight = if (entry.preferred) FontWeight.Bold else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                "${"%.2f".format(Locale.US, entry.sizeMiB)} MiB${if (entry.preferred) " • Base/Universal" else ""}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
    if (convertDialog) {
        SplitConvertDialog(
            file = file,
            leftPath = leftPath,
            rightPath = rightPath,
            sourcePane = sourcePane,
            selectedPaths = selectedPaths,
            onDismiss = { convertDialog = false },
            onStart = { output, options ->
                convertDialog = false
                runTask {
                    val result = withContext(Dispatchers.IO) { SplitPackageTools.convertToApk(file, output, options) }
                    "APK erstellt: ${result.absolutePath}"
                }
            },
        )
    }
    if (decode) ApktoolDecodeDialog(file, leftPanelPath = leftPath, rightPanelPath = rightPath, onDismiss = { decode = false })
}

@Composable
private fun SplitConvertDialog(
    file: File,
    leftPath: String?,
    rightPath: String?,
    sourcePane: String?,
    selectedPaths: Set<String>,
    onDismiss: () -> Unit,
    onStart: (File, SplitPackageTools.ConvertOptions) -> Unit,
) {
    val context = LocalContext.current
    val initial = remember(context) { ApkModulePreferences.load(context) }
    val appName = file.nameWithoutExtension.ifBlank { "project" }
    val sameDir = file.parentFile ?: File(".")
    val defaultDir = File(ApktoolSettings.projectsRoot(context), appName)
    var output by remember(file) { mutableStateOf(uniqueOutput(sameDir, "$appName.apk").absolutePath) }
    var alignment by remember { mutableStateOf(initial.zipAlignment.toString()) }
    var soAlignment by remember { mutableStateOf(initial.sharedLibraryAlignment.toString()) }
    var includeOptional by remember { mutableStateOf(initial.includeOptionalSplits) }
    var includeFeatures by remember { mutableStateOf(initial.includeFeatureSplits) }
    var keepSplits by remember { mutableStateOf(initial.keepExtractedSplits) }
    var cleanMeta by remember { mutableStateOf(initial.cleanMetaInf) }
    var compression by remember { mutableStateOf(initial.compressionLevel.toString()) }
    var zipAlign by remember { mutableStateOf(true) }
    var force by remember { mutableStateOf(initial.zipForce) }
    var verify by remember { mutableStateOf(initial.zipVerify) }

    fun chooseDirectory(dir: File) {
        if (!dir.exists()) dir.mkdirs()
        output = uniqueOutput(dir, "$appName.apk").absolutePath
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${file.extension.uppercase()} → APK") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("Ausgabeort", fontWeight = FontWeight.SemiBold)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { chooseDirectory(sameDir) }) { Text("GLEICHER") }
                    if (!leftPath.isNullOrBlank()) TextButton(onClick = { chooseDirectory(File(leftPath)) }) { Text("LINKS") }
                    if (!rightPath.isNullOrBlank()) TextButton(onClick = { chooseDirectory(File(rightPath)) }) { Text("RECHTS") }
                }
                TextButton(onClick = { chooseDirectory(defaultDir) }) { Text("APP DEFAULT /apktool/projects/$appName") }
                OutlinedTextField(output, { output = it }, label = { Text("Ausgabe-APK") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                Text("AntiSplit-M / Split→APK", fontWeight = FontWeight.SemiBold)
                Text("${selectedPaths.size} Split-APK(s) ausgewählt", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(Modifier.fillMaxWidth().clickable { includeOptional = !includeOptional }, verticalAlignment = Alignment.CenterVertically) { Checkbox(includeOptional, { includeOptional = it }); Text("Optionale Ressourcen/Assets übernehmen") }
                Row(Modifier.fillMaxWidth().clickable { includeFeatures = !includeFeatures }, verticalAlignment = Alignment.CenterVertically) { Checkbox(includeFeatures, { includeFeatures = it }); Text("Feature-Splits übernehmen") }
                Row(Modifier.fillMaxWidth().clickable { cleanMeta = !cleanMeta }, verticalAlignment = Alignment.CenterVertically) { Checkbox(cleanMeta, { cleanMeta = it }); Text("Ungültige META-INF Signaturen entfernen") }
                Row(Modifier.fillMaxWidth().clickable { keepSplits = !keepSplits }, verticalAlignment = Alignment.CenterVertically) { Checkbox(keepSplits, { keepSplits = it }); Text("Extrahierte Splits zusätzlich behalten") }
                OutlinedTextField(compression, { compression = it.filter(Char::isDigit).take(1) }, label = { Text("ZIP-Kompression (0–9)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                Text("zipalign", fontWeight = FontWeight.SemiBold)
                Row(Modifier.fillMaxWidth().clickable { zipAlign = !zipAlign }, verticalAlignment = Alignment.CenterVertically) { Checkbox(zipAlign, { zipAlign = it }); Text("Ergebnis ausrichten") }
                if (zipAlign) {
                    OutlinedTextField(alignment, { alignment = it.filter(Char::isDigit) }, label = { Text("Alignment") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(soAlignment, { soAlignment = it.filter(Char::isDigit) }, label = { Text(".so Page Alignment") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Row(Modifier.fillMaxWidth().clickable { verify = !verify }, verticalAlignment = Alignment.CenterVertically) { Checkbox(verify, { verify = it }); Text("Alignment nachher prüfen") }
                }
                Row(Modifier.fillMaxWidth().clickable { force = !force }, verticalAlignment = Alignment.CenterVertically) { Checkbox(force, { force = it }); Text("Vorhandene Ausgabe überschreiben") }
                if (sourcePane != null) Text("Quelle: ${if (sourcePane == "LEFT") "linkes" else "rechtes"} Panel", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("ABBRECHEN") } },
        confirmButton = {
            TextButton(
                enabled = output.isNotBlank() && (!zipAlign || (alignment.toIntOrNull() ?: 0) > 0),
                onClick = {
                    val updated = initial.copy(
                        zipAlignment = alignment.toIntOrNull() ?: 4,
                        sharedLibraryAlignment = soAlignment.toIntOrNull() ?: 16384,
                        zipForce = force,
                        zipVerify = verify,
                        includeOptionalSplits = includeOptional,
                        includeFeatureSplits = includeFeatures,
                        keepExtractedSplits = keepSplits,
                        cleanMetaInf = cleanMeta,
                        compressionLevel = (compression.toIntOrNull() ?: 6).coerceIn(0, 9),
                    )
                    ApkModulePreferences.save(context, updated)
                    onStart(
                        File(output),
                        SplitPackageTools.ConvertOptions(
                            selectedPaths = selectedPaths,
                            includeOptionalSplits = includeOptional,
                            includeFeatureSplits = includeFeatures,
                            keepExtractedSplits = keepSplits,
                            cleanMetaInf = cleanMeta,
                            compressionLevel = (compression.toIntOrNull() ?: 6).coerceIn(0, 9),
                            zipAlign = zipAlign,
                            alignment = alignment.toIntOrNull() ?: 4,
                            sharedLibraryAlignment = soAlignment.toIntOrNull() ?: 16384,
                            force = force,
                            verifyAlignment = verify,
                        ),
                    )
                },
            ) { Text("START") }
        },
    )
}

private fun uniqueOutput(parent: File, name: String): File {
    var f = File(parent, name)
    var i = 1
    while (f.exists()) { f = File(parent, name.substringBeforeLast('.') + " ($i)." + name.substringAfterLast('.')); i++ }
    return f
}

private fun installSingleApk(context: android.content.Context, file: File) {
    runCatching {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        context.startActivity(Intent(Intent.ACTION_VIEW).apply { setDataAndType(uri, "application/vnd.android.package-archive"); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) })
    }.onFailure { Toast.makeText(context, it.message ?: "Installation fehlgeschlagen", Toast.LENGTH_LONG).show() }
}

private fun share(context: android.content.Context, file: File) {
    runCatching {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension.lowercase()) ?: "*/*"
        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = mime; putExtra(Intent.EXTRA_STREAM, uri); clipData = ClipData.newRawUri(file.name, uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }, "Teilen"))
    }
}

private fun formatSize(bytes: Long): String {
    val mb = bytes / 1024.0 / 1024.0
    return if (mb >= 1) String.format(Locale.US, "%.2f MB", mb) else "${bytes / 1024} KB"
}
