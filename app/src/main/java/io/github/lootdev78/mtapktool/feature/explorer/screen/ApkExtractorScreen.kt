package io.github.lootdev78.mtapktool.feature.explorer.screen

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.Settings
import android.text.format.Formatter
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.graphics.drawable.toBitmap
import io.github.lootdev78.mtapktool.ExplorerOutputBridge
import io.github.lootdev78.mtapktool.apkextractor.ApkExtractorEngine
import io.github.lootdev78.mtapktool.feature.explorer.util.ApkArchiveReader
import io.github.lootdev78.mtapktool.feature.explorer.util.sdkLabel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val EXTRACTOR_PREFS = "mtapktool_installed_apps"

private data class InstalledAppsPreferences(
    val outputPath: String,
    val namePattern: String,
    val verifySignature: Boolean,
    val sortMode: Int,
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ApkExtractorScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefsStore = remember { context.getSharedPreferences(EXTRACTOR_PREFS, Context.MODE_PRIVATE) }
    var prefs by remember { mutableStateOf(loadExtractorPreferences(context)) }
    var apps by remember { mutableStateOf<List<ApkExtractorEngine.AppEntry>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var searchMode by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var systemTab by remember { mutableStateOf(false) }
    var selectedApp by remember { mutableStateOf<ApkExtractorEngine.AppEntry?>(null) }
    var selectedPackages by remember { mutableStateOf<Set<String>>(emptySet()) }
    var overflow by remember { mutableStateOf(false) }
    var showPreferences by remember { mutableStateOf(false) }
    var showSort by remember { mutableStateOf(false) }
    var showExtractConfirm by remember { mutableStateOf(false) }
    var showUninstallConfirm by remember { mutableStateOf(false) }
    var busyText by remember { mutableStateOf<String?>(null) }
    var busyProgress by remember { mutableStateOf(0f) }

    fun reload() {
        loading = true
        scope.launch {
            apps = withContext(Dispatchers.IO) { ApkExtractorEngine.listInstalled(context, true) }
            loading = false
        }
    }
    LaunchedEffect(Unit) { reload() }

    val visible = remember(apps, query, systemTab, prefs.sortMode) {
        val filtered = apps.asSequence()
            .filter { it.system == systemTab }
            .filter { query.isBlank() || it.label.contains(query, true) || it.packageName.contains(query, true) }
            .toList()
        when (prefs.sortMode) {
            1 -> filtered.sortedByDescending { it.baseSize + it.splitSize }
            2 -> filtered.sortedBy { it.packageName.lowercase(Locale.ROOT) }
            3 -> filtered.sortedByDescending { it.lastUpdateTime }
            else -> filtered.sortedBy { it.label.lowercase(Locale.ROOT) }
        }
    }

    fun toggleSelected(app: ApkExtractorEngine.AppEntry) {
        selectedPackages = if (app.packageName in selectedPackages) selectedPackages - app.packageName else selectedPackages + app.packageName
    }

    fun launchApp(app: ApkExtractorEngine.AppEntry) {
        val launch = context.packageManager.getLaunchIntentForPackage(app.packageName)
        if (launch != null) context.startActivity(launch)
        else Toast.makeText(context, "This application has no launch activity", Toast.LENGTH_SHORT).show()
    }

    fun openDetails(app: ApkExtractorEngine.AppEntry) {
        context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${app.packageName}")))
    }

    fun uninstallApp(app: ApkExtractorEngine.AppEntry) {
        context.startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:${app.packageName}")))
    }

    suspend fun extractApp(app: ApkExtractorEngine.AppEntry): File {
        val root = File(prefs.outputPath.ifBlank { ApkExtractorEngine.defaultOutputRoot().absolutePath })
        val fileName = outputNameFor(app, prefs.namePattern, app.split)
        return withContext(Dispatchers.IO) {
            if (app.split) ApkExtractorEngine.createApks(context, app.packageName, root, 6, fileName)
            else ApkExtractorEngine.extractBase(context, app.packageName, root, fileName)
        }
    }

    fun extractSingle(app: ApkExtractorEngine.AppEntry) {
        busyText = "Extracting ${app.label}…"
        busyProgress = 0f
        scope.launch {
            val result = runCatching { extractApp(app) }
            busyProgress = 1f
            busyText = null
            result.onSuccess { out ->
                Toast.makeText(context, "Saved: ${out.absolutePath}", Toast.LENGTH_LONG).show()
                ExplorerOutputBridge.publish(out.absolutePath)
                selectedApp = null
                onBack()
            }.onFailure { Toast.makeText(context, it.message ?: "Extraction failed", Toast.LENGTH_LONG).show() }
        }
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(Modifier.fillMaxSize()) {
            Surface(color = MaterialTheme.colorScheme.surface, contentColor = MaterialTheme.colorScheme.onSurface) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = {
                        if (selectedPackages.isNotEmpty()) selectedPackages = emptySet() else onBack()
                    }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                    if (searchMode) {
                        TextField(
                            value = query,
                            onValueChange = { query = it },
                            placeholder = { Text(io.github.lootdev78.mtapktool.core.i18n.UiText.auto("Search apps")) },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = { searchMode = false; query = "" }) { Icon(Icons.Default.Close, "Close search") }
                    } else {
                        Text(
                            if (selectedPackages.isEmpty()) "Installed Apps" else "Selected: ${selectedPackages.size}",
                            modifier = Modifier.weight(1f),
                            fontSize = 27.sp,
                            fontWeight = FontWeight.Medium,
                        )
                        IconButton(onClick = { searchMode = true }) { Icon(Icons.Default.Search, "Search") }
                        Box {
                            IconButton(onClick = { overflow = true }) { Icon(Icons.Default.MoreVert, "More") }
                            DropdownMenu(expanded = overflow, onDismissRequest = { overflow = false }) {
                                DropdownMenuItem(
                                    text = { Text(io.github.lootdev78.mtapktool.core.i18n.UiText.auto("Sort")) },
                                    leadingIcon = { Icon(Icons.Default.Sort, null) },
                                    onClick = { overflow = false; showSort = true },
                                )
                                DropdownMenuItem(
                                    text = { Text(io.github.lootdev78.mtapktool.core.i18n.UiText.auto("Select all")) },
                                    leadingIcon = { Icon(Icons.Default.SelectAll, null) },
                                    onClick = {
                                        overflow = false
                                        selectedPackages = visible.mapTo(linkedSetOf()) { it.packageName }
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(io.github.lootdev78.mtapktool.core.i18n.UiText.auto("Preferences")) },
                                    leadingIcon = { Icon(Icons.Default.Settings, null) },
                                    onClick = { overflow = false; showPreferences = true },
                                )
                            }
                        }
                    }
                }
            }

            Row(Modifier.fillMaxWidth()) {
                ExtractorTab("USER APP", !systemTab) { systemTab = false; selectedPackages = emptySet() }
                ExtractorTab("SYSTEM APP", systemTab) { systemTab = true; selectedPackages = emptySet() }
            }

            if (loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else {
                LazyColumn(Modifier.fillMaxSize().padding(horizontal = 7.dp)) {
                    items(visible, key = { it.packageName }) { app ->
                        val icon by produceState<android.graphics.Bitmap?>(null, app.packageName) {
                            value = withContext(Dispatchers.IO) {
                                runCatching { context.packageManager.getApplicationIcon(app.packageName).toBitmap(84, 84) }.getOrNull()
                            }
                        }
                        val checked = app.packageName in selectedPackages
                        Surface(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).combinedClickable(
                                onClick = {
                                    if (selectedPackages.isNotEmpty()) toggleSelected(app) else selectedApp = app
                                },
                                onLongClick = { toggleSelected(app) },
                            ),
                            color = if (checked) MaterialTheme.colorScheme.primary.copy(alpha = 0.28f) else MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(2.dp),
                        ) {
                            Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                                if (icon != null) Image(icon!!.asImageBitmap(), null, modifier = Modifier.size(56.dp))
                                else Icon(Icons.Default.Android, null, modifier = Modifier.size(56.dp))
                                Spacer(Modifier.width(11.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(app.label, fontSize = 21.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(app.versionName.ifBlank { "-" }, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 15.sp)
                                        Spacer(Modifier.width(12.dp))
                                        Text(formatBytes(context, app.baseSize), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 15.sp)
                                        if (app.split && app.splitSize > 0L) {
                                            Spacer(Modifier.width(10.dp))
                                            Text(io.github.lootdev78.mtapktool.core.i18n.UiText.auto("SPLIT+${formatBytes(context, app.splitSize)}"), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 15.sp)
                                        }
                                    }
                                    Text(app.packageName, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
            }
        }

        if (selectedPackages.isNotEmpty()) {
            Column(
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 14.dp, bottom = 18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalAlignment = Alignment.End,
            ) {
                SmallFloatingActionButton(
                    onClick = { selectedPackages = emptySet() },
                    containerColor = Color(0xFFD33B3B),
                    contentColor = Color.White,
                ) { Icon(Icons.Default.Close, "Cancel selection") }
                SmallFloatingActionButton(
                    onClick = { showUninstallConfirm = true },
                    containerColor = Color(0xFFD48624),
                    contentColor = Color.White,
                ) { Icon(Icons.Default.Delete, "Uninstall") }
                SmallFloatingActionButton(
                    onClick = { selectedPackages = visible.mapTo(linkedSetOf()) { it.packageName } },
                    containerColor = Color(0xFF1976D2),
                    contentColor = Color.White,
                ) { Icon(Icons.Default.SelectAll, "Select all") }
                SmallFloatingActionButton(
                    onClick = { showExtractConfirm = true },
                    containerColor = Color(0xFF1976D2),
                    contentColor = Color.White,
                ) { Icon(Icons.Default.FileDownload, "Extract") }
            }
        }
    }

    selectedApp?.let { app ->
        InstalledAppInfoDialog(
            app = app,
            verifySignature = prefs.verifySignature,
            onDismiss = { selectedApp = null },
            onLaunch = { launchApp(app) },
            onDetails = { openDetails(app) },
            onUninstall = { uninstallApp(app) },
            onExtract = { extractSingle(app) },
        )
    }

    if (showPreferences) {
        InstalledAppsPreferencesDialog(
            initial = prefs,
            onDismiss = { showPreferences = false },
            onSave = { value ->
                prefs = value
                prefsStore.edit()
                    .putString("outputPath", value.outputPath)
                    .putString("namePattern", value.namePattern)
                    .putBoolean("verifySignature", value.verifySignature)
                    .putInt("sortMode", value.sortMode)
                    .apply()
                showPreferences = false
            },
        )
    }

    if (showSort) {
        ExtractorSortDialog(
            selected = prefs.sortMode,
            onDismiss = { showSort = false },
            onSelect = { mode ->
                prefs = prefs.copy(sortMode = mode)
                prefsStore.edit().putInt("sortMode", mode).apply()
                showSort = false
            },
        )
    }

    if (showExtractConfirm) {
        ConfirmExtractorDialog(
            title = "Info",
            text = "Do you want to extract the selected ${selectedPackages.size} apk files?",
            onCancel = { showExtractConfirm = false },
            onConfirm = {
                showExtractConfirm = false
                val targets = apps.filter { it.packageName in selectedPackages }
                busyText = "Extracting ${targets.size} apps…"
                busyProgress = 0f
                scope.launch {
                    var last: File? = null
                    var failures = 0
                    targets.forEachIndexed { index, app ->
                        busyText = "Extracting ${app.label}…"
                        runCatching { extractApp(app) }.onSuccess { last = it }.onFailure { failures++ }
                        busyProgress = (index + 1f) / targets.size.coerceAtLeast(1)
                    }
                    busyText = null
                    selectedPackages = emptySet()
                    last?.let { ExplorerOutputBridge.publish(it.absolutePath) }
                    Toast.makeText(context, if (failures == 0) "Extraction complete" else "Extraction complete ($failures failed)", Toast.LENGTH_LONG).show()
                    if (last != null) onBack()
                }
            },
        )
    }

    if (showUninstallConfirm) {
        ConfirmExtractorDialog(
            title = "Info",
            text = "Do you want to uninstall the selected ${selectedPackages.size} applications?",
            onCancel = { showUninstallConfirm = false },
            onConfirm = {
                showUninstallConfirm = false
                apps.filter { it.packageName in selectedPackages }.forEach(::uninstallApp)
                selectedPackages = emptySet()
            },
        )
    }

    busyText?.let { text ->
        Dialog(onDismissRequest = {}, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Surface(
                modifier = Modifier.fillMaxWidth(0.78f),
                shape = RoundedCornerShape(2.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                shadowElevation = 10.dp,
            ) {
                Column(Modifier.padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(Modifier.size(14.dp))
                    Text(text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(io.github.lootdev78.mtapktool.core.i18n.UiText.auto("${(busyProgress * 100).toInt().coerceIn(0, 100)}%"), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun RowScope.ExtractorTab(label: String, selected: Boolean, onClick: () -> Unit) {
    Column(
        Modifier.weight(1f).clickable(onClick = onClick).padding(top = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(label, fontSize = 17.sp, fontWeight = FontWeight.Medium, color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.size(12.dp))
        Box(Modifier.fillMaxWidth().height(if (selected) 3.dp else 1.dp).background(if (selected) Color(0xFF1976D2) else Color.Transparent))
    }
}

@Composable
private fun InstalledAppInfoDialog(
    app: ApkExtractorEngine.AppEntry,
    verifySignature: Boolean,
    onDismiss: () -> Unit,
    onLaunch: () -> Unit,
    onDetails: () -> Unit,
    onUninstall: () -> Unit,
    onExtract: () -> Unit,
) {
    val context = LocalContext.current
    var more by remember { mutableStateOf(false) }
    val icon by produceState<android.graphics.Bitmap?>(null, app.packageName) {
        value = withContext(Dispatchers.IO) { runCatching { context.packageManager.getApplicationIcon(app.packageName).toBitmap(104, 104) }.getOrNull() }
    }
    val signature by produceState(if (verifySignature) "Checking…" else "Not checked", app.packageName, verifySignature) {
        value = if (!verifySignature || app.sourceDir.isNullOrBlank()) "Not checked" else withContext(Dispatchers.IO) {
            ApkArchiveReader.signatureInfo(File(app.sourceDir))?.schemes ?: "Unknown"
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.9f),
            shape = RoundedCornerShape(2.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            shadowElevation = 10.dp,
        ) {
            Column(Modifier.fillMaxWidth().padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (icon != null) Image(icon!!.asImageBitmap(), null, modifier = Modifier.size(64.dp))
                    else Icon(Icons.Default.Android, null, modifier = Modifier.size(64.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(app.label, fontSize = 26.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(app.versionName.ifBlank { "-" }, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 17.sp)
                    }
                }
                Spacer(Modifier.size(12.dp))
                Column(Modifier.heightIn(max = 540.dp).verticalScroll(rememberScrollState())) {
                    ExtractorInfoRow("Package name", app.packageName)
                    ExtractorInfoRow("Version code", app.versionCode.toString())
                    ExtractorInfoRow("File size", formatBytes(context, app.baseSize + app.splitSize))
                    ExtractorInfoRow("Signature", signature)
                    ExtractorInfoRow("Protection", "No Detected")
                    ExtractorInfoRow("Target SDK", sdkLabel(app.targetSdk))
                    ExtractorInfoRow("Minimum SDK", sdkLabel(app.minSdk))
                    ExtractorInfoRow("Data directory 1", app.dataDir ?: "-")
                    ExtractorInfoRow("Data directory 2", "/storage/emulated/0/Android/data/${app.packageName}")
                    ExtractorInfoRow("APK path", app.sourceDir ?: "-")
                    ExtractorInfoRow("First install", extractorDate(app.firstInstallTime))
                    ExtractorInfoRow("Last update", extractorDate(app.lastUpdateTime))
                    ExtractorInfoRow("UID", app.uid.toString())
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box {
                        TextButton(onClick = { more = true }) { Text(io.github.lootdev78.mtapktool.core.i18n.UiText.auto("MORE")) }
                        DropdownMenu(expanded = more, onDismissRequest = { more = false }) {
                            DropdownMenuItem(text = { Text(io.github.lootdev78.mtapktool.core.i18n.UiText.auto("Launch")) }, onClick = { more = false; onLaunch() })
                            DropdownMenuItem(text = { Text(io.github.lootdev78.mtapktool.core.i18n.UiText.auto("Details")) }, onClick = { more = false; onDetails() })
                            DropdownMenuItem(text = { Text(io.github.lootdev78.mtapktool.core.i18n.UiText.auto("Uninstall")) }, onClick = { more = false; onUninstall() })
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onExtract) { Text(io.github.lootdev78.mtapktool.core.i18n.UiText.auto("EXTRACT APK")) }
                }
            }
        }
    }
}

@Composable
private fun InstalledAppsPreferencesDialog(
    initial: InstalledAppsPreferences,
    onDismiss: () -> Unit,
    onSave: (InstalledAppsPreferences) -> Unit,
) {
    val context = LocalContext.current
    var value by remember(initial) { mutableStateOf(initial) }
    val treePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            }
            val path = uriToLocalTreePath(uri)
            if (path != null) value = value.copy(outputPath = path)
            else Toast.makeText(context, "This extractor requires a local filesystem directory", Toast.LENGTH_LONG).show()
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.9f),
            shape = RoundedCornerShape(2.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            shadowElevation = 10.dp,
        ) {
            Column(Modifier.fillMaxWidth().padding(18.dp)) {
                Text(io.github.lootdev78.mtapktool.core.i18n.UiText.auto("Preferences"), fontSize = 28.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.size(14.dp))
                Text(io.github.lootdev78.mtapktool.core.i18n.UiText.auto("Installed Apps"), color = Color(0xFF2196F3), fontSize = 17.sp)
                Spacer(Modifier.size(10.dp))
                Text(io.github.lootdev78.mtapktool.core.i18n.UiText.auto("APK storage path"), fontSize = 19.sp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextField(
                        value = value.outputPath,
                        onValueChange = { value = value.copy(outputPath = it) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = { treePicker.launch(null) }) { Text(io.github.lootdev78.mtapktool.core.i18n.UiText.auto("…"), fontSize = 22.sp) }
                }
                Spacer(Modifier.size(10.dp))
                Text(io.github.lootdev78.mtapktool.core.i18n.UiText.auto("Name pattern"), fontSize = 19.sp)
                TextField(
                    value = value.namePattern,
                    onValueChange = { value = value.copy(namePattern = it) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "{A} Application name\n{P} Package name\n{V} Version name\n{C} Version code",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(top = 8.dp),
                )
                HorizontalDivider(Modifier.padding(vertical = 14.dp))
                Text(io.github.lootdev78.mtapktool.core.i18n.UiText.auto("Other"), color = Color(0xFF2196F3), fontSize = 17.sp)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(io.github.lootdev78.mtapktool.core.i18n.UiText.auto("Enable signature verification"), modifier = Modifier.weight(1f), fontSize = 18.sp)
                    Switch(checked = value.verifySignature, onCheckedChange = { value = value.copy(verifySignature = it) })
                }
                Text(
                    "When enabled, the signature validity will be calculated when viewing APK information. Larger files take longer to calculate. When disabled, the calculation step is skipped. Normally this does not need to be enabled because APKs with invalid signatures cannot be installed successfully unless you have modified the system.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp,
                    lineHeight = 18.sp,
                )
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { value = value.copy(namePattern = "{A}_{V}.apk") }) { Text(io.github.lootdev78.mtapktool.core.i18n.UiText.auto("{ }"), fontSize = 20.sp) }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onDismiss) { Text(io.github.lootdev78.mtapktool.core.i18n.UiText.auto("CANCEL")) }
                    TextButton(onClick = { onSave(value) }) { Text(io.github.lootdev78.mtapktool.core.i18n.UiText.auto("OK")) }
                }
            }
        }
    }
}

@Composable
private fun ExtractorSortDialog(selected: Int, onDismiss: () -> Unit, onSelect: (Int) -> Unit) {
    val options = listOf("Name", "File size", "Package name", "Last update")
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.82f),
            shape = RoundedCornerShape(2.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            shadowElevation = 10.dp,
        ) {
            Column(Modifier.padding(20.dp)) {
                Text(io.github.lootdev78.mtapktool.core.i18n.UiText.auto("Sort"), fontSize = 27.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.size(8.dp))
                options.forEachIndexed { index, label ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onSelect(index) }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = selected == index, onClick = { onSelect(index) })
                        Spacer(Modifier.width(10.dp))
                        Text(label, fontSize = 19.sp)
                    }
                }
                Row(Modifier.fillMaxWidth()) {
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onDismiss) { Text(io.github.lootdev78.mtapktool.core.i18n.UiText.auto("CANCEL")) }
                }
            }
        }
    }
}

@Composable
private fun ConfirmExtractorDialog(title: String, text: String, onCancel: () -> Unit, onConfirm: () -> Unit) {
    Dialog(onDismissRequest = onCancel, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.9f),
            shape = RoundedCornerShape(2.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            shadowElevation = 10.dp,
        ) {
            Column(Modifier.padding(22.dp)) {
                Text(title, fontSize = 28.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.size(12.dp))
                Text(text, fontSize = 20.sp)
                Spacer(Modifier.size(18.dp))
                Row(Modifier.fillMaxWidth()) {
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onCancel) { Text(io.github.lootdev78.mtapktool.core.i18n.UiText.auto("CANCEL")) }
                    TextButton(onClick = onConfirm) { Text(io.github.lootdev78.mtapktool.core.i18n.UiText.auto("OK")) }
                }
            }
        }
    }
}

@Composable
private fun ExtractorInfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.Top) {
        Text(label, modifier = Modifier.width(138.dp), fontSize = 18.sp)
        Text(value, modifier = Modifier.weight(1f), fontSize = 17.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

private fun loadExtractorPreferences(context: Context): InstalledAppsPreferences {
    val p = context.getSharedPreferences(EXTRACTOR_PREFS, Context.MODE_PRIVATE)
    return InstalledAppsPreferences(
        outputPath = p.getString("outputPath", ApkExtractorEngine.defaultOutputRoot().absolutePath) ?: ApkExtractorEngine.defaultOutputRoot().absolutePath,
        namePattern = p.getString("namePattern", "{A}_{V}.apk") ?: "{A}_{V}.apk",
        verifySignature = p.getBoolean("verifySignature", false),
        sortMode = p.getInt("sortMode", 0).coerceIn(0, 3),
    )
}

private fun outputNameFor(app: ApkExtractorEngine.AppEntry, pattern: String, split: Boolean): String {
    fun clean(value: String) = value.replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_").trim().ifBlank { "app" }
    var name = pattern.ifBlank { "{A}_{V}.apk" }
        .replace("{A}", clean(app.label))
        .replace("{P}", clean(app.packageName))
        .replace("{V}", clean(app.versionName.ifBlank { "0" }))
        .replace("{C}", app.versionCode.toString())
    if (split) {
        if (name.endsWith(".apk", true)) name = name.dropLast(4) + ".apks"
        if (!name.endsWith(".apks", true)) name += ".apks"
    } else if (!name.endsWith(".apk", true)) {
        name += ".apk"
    }
    return name
}

private fun formatBytes(context: Context, value: Long): String = Formatter.formatShortFileSize(context, value.coerceAtLeast(0L))

private fun extractorDate(value: Long): String =
    if (value <= 0L) "-" else SimpleDateFormat("dd-MM-yyyy HH:mm:ss", Locale.getDefault()).format(Date(value))

private fun uriToLocalTreePath(uri: Uri): String? {
    if (uri.authority != "com.android.externalstorage.documents") return null
    val id = runCatching { DocumentsContract.getTreeDocumentId(uri) }.getOrNull() ?: return null
    val parts = id.split(':', limit = 2)
    val volume = parts.getOrNull(0) ?: return null
    val relative = parts.getOrNull(1).orEmpty()
    val root = if (volume.equals("primary", true)) Environment.getExternalStorageDirectory().absolutePath else "/storage/$volume"
    return if (relative.isBlank()) root else "$root/$relative"
}
