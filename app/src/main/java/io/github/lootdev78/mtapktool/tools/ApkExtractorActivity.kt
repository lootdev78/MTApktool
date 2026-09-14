package io.github.lootdev78.mtapktool.tools

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import io.github.lootdev78.mtapktool.apkextractor.ApkExtractorEngine
import io.github.lootdev78.mtapktool.apktool.ApkModulePreferences
import io.github.lootdev78.mtapktool.core.theme.MTApktoolTheme
import io.github.lootdev78.mtapktool.settings.ApkExtractorSettingsActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.DateFormat
import java.util.Date

class ApkExtractorActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MTApktoolTheme { ApkExtractorScreen(onBack = ::finish) } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ApkExtractorScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var options by remember { mutableStateOf(ApkExtractorPreferences.load(context)) }
    var moduleOptions by remember { mutableStateOf(ApkModulePreferences.load(context)) }
    var apps by remember { mutableStateOf<List<ApkExtractorEngine.AppEntry>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<ApkExtractorEngine.AppEntry?>(null) }
    var extraFunctions by remember { mutableStateOf<ApkExtractorEngine.AppEntry?>(null) }
    var splitPicker by remember { mutableStateOf<Pair<ApkExtractorEngine.AppEntry, List<File>>?>(null) }
    var status by remember { mutableStateOf<String?>(null) }

    fun reload() {
        loading = true
        options = ApkExtractorPreferences.load(context)
        moduleOptions = ApkModulePreferences.load(context)
        scope.launch {
            apps = withContext(Dispatchers.IO) {
                ApkExtractorEngine.listInstalledApps(context, options.includeSystemApps)
            }
            loading = false
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) reload()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun runExtractorOperation(
        app: ApkExtractorEngine.AppEntry,
        label: String,
        operation: () -> File,
    ) {
        selected = null
        extraFunctions = null
        status = label
        scope.launch {
            val result = withContext(Dispatchers.IO) { runCatching(operation) }
            result.onSuccess { out -> Toast.makeText(context, "Gespeichert: ${out.absolutePath}", Toast.LENGTH_LONG).show() }
                .onFailure { Toast.makeText(context, it.message ?: "$label fehlgeschlagen", Toast.LENGTH_LONG).show() }
            status = null
        }
    }

    val filtered = remember(apps, query, options.sortMode) {
        val matches = if (query.isBlank()) apps else apps.filter {
            it.label.contains(query, true) || it.packageName.contains(query, true)
        }
        when (options.sortMode) {
            "last_update" -> matches.sortedByDescending { it.lastUpdateTime }
            "first_install" -> matches.sortedByDescending { it.firstInstallTime }
            else -> matches.sortedBy { it.label.lowercase() }
        }
    }
    val dateFormat = remember { DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("APK Extractor") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück") } },
                actions = {
                    IconButton(onClick = { reload() }) { Icon(Icons.Default.Refresh, "Aktualisieren") }
                    IconButton(onClick = { context.startActivity(Intent(context, ApkExtractorSettingsActivity::class.java)) }) {
                        Icon(Icons.Default.Settings, "Einstellungen")
                    }
                },
            )
        },
    ) { insets ->
        Column(Modifier.fillMaxSize().padding(insets)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                placeholder = { Text("Apps durchsuchen") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(12.dp),
            )
            Text(
                "Ausgabe: ${options.outputRoot}/<app>.apk bzw. <app>.apks",
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 2.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (loading) {
                Row(Modifier.fillMaxWidth().padding(24.dp), horizontalArrangement = Arrangement.Center) { CircularProgressIndicator() }
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(filtered, key = { it.packageName }) { app ->
                        Row(
                            Modifier.fillMaxWidth().clickable { selected = app }.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (options.showIcon) {
                                AndroidView(
                                    factory = { viewContext -> ImageView(viewContext).apply { scaleType = ImageView.ScaleType.FIT_CENTER } },
                                    update = { imageView ->
                                        runCatching { imageView.setImageDrawable(context.packageManager.getApplicationIcon(app.packageName)) }
                                            .onFailure { imageView.setImageDrawable(null) }
                                    },
                                    modifier = Modifier.size(38.dp),
                                )
                                Spacer(Modifier.width(12.dp))
                            } else {
                                Icon(Icons.Default.Android, null, modifier = Modifier.size(30.dp))
                                Spacer(Modifier.width(10.dp))
                            }
                            Column(Modifier.weight(1f)) {
                                val primary = when {
                                    options.showAppName -> app.label
                                    options.showPackageName -> app.packageName
                                    else -> app.label
                                }
                                Text(primary, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                                if (options.showPackageName && primary != app.packageName) {
                                    Text(app.packageName, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                val versionParts = buildList {
                                    if (options.showVersionName && app.versionName.isNotBlank()) add("v${app.versionName}")
                                    if (options.showVersionCode) add("code ${app.versionCode}")
                                }
                                if (versionParts.isNotEmpty()) {
                                    Text(versionParts.joinToString(" · "), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                if (options.showFirstInstall) {
                                    Text("Installiert: ${dateFormat.format(Date(app.firstInstallTime))}", maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                if (options.showLastUpdate) {
                                    Text("Update: ${dateFormat.format(Date(app.lastUpdateTime))}", maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            if (app.split) Text("SPLIT", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }

    selected?.let { app ->
        AlertDialog(
            onDismissRequest = { selected = null },
            title = { Text(app.label) },
            text = {
                Column {
                    Text(app.packageName, style = MaterialTheme.typography.bodySmall)
                    Text(if (app.split) "Split APK installiert" else "Einzelne APK installiert", modifier = Modifier.padding(top = 8.dp))
                    Text("Standardziel: ${options.outputRoot}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
                }
            },
            dismissButton = { TextButton(onClick = { selected = null }) { Text("SCHLIESSEN") } },
            confirmButton = {
                Row {
                    TextButton(onClick = {
                        selected = null
                        status = "APKExtractor: ${app.label} …"
                        scope.launch {
                            runCatching {
                                withContext(Dispatchers.IO) {
                                    ApkExtractorEngine.extract(
                                        context,
                                        app.packageName,
                                        File(options.outputRoot),
                                        if (app.split && options.defaultSplitMode == "merge") ApkExtractorEngine.SplitMode.MERGED_APK
                                        else if (app.split) ApkExtractorEngine.SplitMode.APKS_ARCHIVE
                                        else ApkExtractorEngine.SplitMode.BASE_APK_ONLY,
                                        options.compressionLevel,
                                        moduleOptions.antiSplitForceMerge,
                                        moduleOptions.antiSplitStripMetadata,
                                        moduleOptions.cleanMetaInf,
                                    ) { message -> scope.launch(Dispatchers.Main) { status = message } }
                                }
                            }.onSuccess { out -> Toast.makeText(context, "Gespeichert: ${out.absolutePath}", Toast.LENGTH_LONG).show() }
                                .onFailure { Toast.makeText(context, it.message ?: "APK Extract fehlgeschlagen", Toast.LENGTH_LONG).show() }
                            status = null
                        }
                    }) { Text(if (app.split && options.defaultSplitMode == "apks") "APKS" else "EXTRAHIEREN") }
                    if (app.split) TextButton(onClick = {
                        selected = null
                        status = "AntiSplit-M …"
                        scope.launch {
                            runCatching {
                                withContext(Dispatchers.IO) {
                                    ApkExtractorEngine.extract(
                                        context,
                                        app.packageName,
                                        File(options.outputRoot),
                                        ApkExtractorEngine.SplitMode.MERGED_APK,
                                        options.compressionLevel,
                                        moduleOptions.antiSplitForceMerge,
                                        moduleOptions.antiSplitStripMetadata,
                                        moduleOptions.cleanMetaInf,
                                    ) { message -> scope.launch(Dispatchers.Main) { status = message } }
                                }
                            }.onSuccess { out -> Toast.makeText(context, "APK erstellt: ${out.absolutePath}", Toast.LENGTH_LONG).show() }
                                .onFailure { Toast.makeText(context, it.message ?: "Merge fehlgeschlagen", Toast.LENGTH_LONG).show() }
                            status = null
                        }
                    }) { Text("MERGE APK") }
                    TextButton(onClick = { selected = null; extraFunctions = app }) { Text("FUNKTIONEN") }
                    TextButton(onClick = {
                        selected = null
                        context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${app.packageName}")))
                    }) { Text("INFO") }
                }
            },
        )
    }

    extraFunctions?.let { app ->
        AlertDialog(
            onDismissRequest = { extraFunctions = null },
            title = { Text("APK Extractor – Funktionen") },
            text = {
                Column {
                    if (options.showExtractBase) ExtractorFunctionRow("Base APK extrahieren") {
                        runExtractorOperation(app, "Base APK …") {
                            ApkExtractorEngine.extractSplitApk(context, app.packageName, 0, File(options.outputRoot)) { message ->
                                scope.launch(Dispatchers.Main) { status = message }
                            }
                        }
                    }
                    if (options.showExtractSplit && app.split) ExtractorFunctionRow("Einzelnen Split extrahieren") {
                        scope.launch {
                            val files = withContext(Dispatchers.IO) { runCatching { ApkExtractorEngine.installedApkFiles(context, app.packageName) }.getOrElse { emptyList() } }
                            extraFunctions = null
                            splitPicker = app to files
                        }
                    }
                    if (options.showExtractIcon) ExtractorFunctionRow("App-Icon als PNG extrahieren") {
                        runExtractorOperation(app, "Icon …") {
                            ApkExtractorEngine.extractIcon(context, app.packageName, File(options.outputRoot)) { message ->
                                scope.launch(Dispatchers.Main) { status = message }
                            }
                        }
                    }
                    if (options.showExtractManifest) ExtractorFunctionRow("AndroidManifest.xml extrahieren") {
                        runExtractorOperation(app, "Manifest …") {
                            ApkExtractorEngine.extractManifest(context, app.packageName, File(options.outputRoot)) { message ->
                                scope.launch(Dispatchers.Main) { status = message }
                            }
                        }
                    }
                    if (options.showExtractDex) ExtractorFunctionRow("DEX als ZIP extrahieren") {
                        runExtractorOperation(app, "DEX …") {
                            ApkExtractorEngine.extractDex(context, app.packageName, File(options.outputRoot), options.compressionLevel) { message ->
                                scope.launch(Dispatchers.Main) { status = message }
                            }
                        }
                    }
                    if (options.showExtractResources) ExtractorFunctionRow("Ressourcen als ZIP extrahieren") {
                        runExtractorOperation(app, "Ressourcen …") {
                            ApkExtractorEngine.extractResources(context, app.packageName, File(options.outputRoot), options.compressionLevel) { message ->
                                scope.launch(Dispatchers.Main) { status = message }
                            }
                        }
                    }
                    if (options.showExtractLibs) ExtractorFunctionRow("Native Libraries als ZIP extrahieren") {
                        runExtractorOperation(app, "Libraries …") {
                            ApkExtractorEngine.extractLibraries(context, app.packageName, File(options.outputRoot), options.compressionLevel) { message ->
                                scope.launch(Dispatchers.Main) { status = message }
                            }
                        }
                    }
                    if (!options.showExtractBase && !(options.showExtractSplit && app.split) && !options.showExtractIcon && !options.showExtractManifest && !options.showExtractDex && !options.showExtractResources && !options.showExtractLibs) {
                        Text("Keine Zusatzfunktionen aktiviert. Einstellungen → APK Extractor.")
                    }
                }
            },
            confirmButton = { TextButton(onClick = { extraFunctions = null }) { Text("SCHLIESSEN") } },
        )
    }

    splitPicker?.let { (app, files) ->
        AlertDialog(
            onDismissRequest = { splitPicker = null },
            title = { Text("Split auswählen") },
            text = {
                Column {
                    if (files.isEmpty()) Text("Keine Split-APKs gefunden")
                    files.forEachIndexed { index, file ->
                        ExtractorFunctionRow(if (index == 0) "Base: ${file.name}" else file.name) {
                            splitPicker = null
                            runExtractorOperation(app, "${file.name} …") {
                                ApkExtractorEngine.extractSplitApk(context, app.packageName, index, File(options.outputRoot)) { message ->
                                    scope.launch(Dispatchers.Main) { status = message }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { splitPicker = null }) { Text("SCHLIESSEN") } },
        )
    }

    status?.let { text ->
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Verarbeitung") },
            text = { Row(verticalAlignment = Alignment.CenterVertically) { CircularProgressIndicator(Modifier.size(26.dp)); Spacer(Modifier.width(12.dp)); Text(text) } },
            confirmButton = {},
        )
    }
}

@Composable
private fun ExtractorFunctionRow(label: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 4.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}
