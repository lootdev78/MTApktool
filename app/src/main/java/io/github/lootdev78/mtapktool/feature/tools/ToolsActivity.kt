package io.github.lootdev78.mtapktool.feature.tools

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionConfig
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.lootdev78.mtapktool.core.theme.MTExplorerTheme
import io.github.lootdev78.mtapktool.core.theme.MtClassicAlertDialog
import io.github.lootdev78.mtapktool.core.theme.ThemeManager
import io.github.lootdev78.mtapktool.core.theme.ThemeMode
import io.github.lootdev78.mtapktool.settings.ExplorerPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ToolsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState); enableEdgeToEdge()
        ExplorerPreferences.init(this)
        val theme = ThemeManager(this)
        setContent {
            val mode by theme.themeModeFlow.collectAsState(initial = ThemeMode.SYSTEM)
            val preferences by ExplorerPreferences.state.collectAsState()
            MTExplorerTheme(themeMode = mode, accentKey = preferences.accentKey) {
                val colors = MaterialTheme.colorScheme
                SideEffect { getSharedPreferences("mt_tools_palette", 0).edit().putInt("surface", colors.surface.toArgb()).putInt("text", colors.onSurface.toArgb()).putInt("accent", colors.primary.toArgb()).apply() }
                Surface(Modifier.fillMaxSize()) {
                    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                        Row(Modifier.fillMaxWidth().height(48.dp).background(colors.surfaceVariant)) {
                            IconButton(onClick = { finish() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück") }
                            Text(if (intent.getStringExtra("tool") == "color") "Color Picker" else "Layout Inspector", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 13.dp))
                        }
                        if (intent.getStringExtra("tool") == "color") ColorPickerContent(intent.getBooleanExtra("pick", false)) { color ->
                            setResult(RESULT_OK, Intent().putExtra("color", color)); finish()
                        } else InspectorContent()
                    }
                }
            }
        }
    }
    companion object {
        fun intent(context: Context, tool: String) = Intent(context, ToolsActivity::class.java).putExtra("tool", tool)
    }
}

@Composable
private fun InspectorContent() {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val running by InspectorState.running.collectAsState()
    val nodes by InspectorState.nodes.collectAsState()
    val activity by InspectorState.activity.collectAsState()
    var ready by remember { mutableStateOf(InspectorAccessibilityService.instance != null) }
    var selected by remember { mutableStateOf<InspectedNode?>(null) }
    var export by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            val outcome = withContext(Dispatchers.IO) { runCatching { context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(export) } ?: error("Datei nicht beschreibbar") } }
            Toast.makeText(context, if (outcome.isSuccess) "Layout exportiert" else outcome.exceptionOrNull()?.message, Toast.LENGTH_LONG).show()
        }
    }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) ready = InspectorAccessibilityService.instance != null }
        owner.lifecycle.addObserver(observer); onDispose { owner.lifecycle.removeObserver(observer) }
    }
    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Text("Ansichtshierarchie, IDs, Texte, Zustände und Bounds. Die Inspektion liest die Android-Bedienungshilfen; Passwortfelder werden ausgeblendet.", style = MaterialTheme.typography.bodySmall)
        if (!ready) TextButton(onClick = { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }) { Text("BEDIENUNGSHILFE AKTIVIEREN") }
        Row {
            TextButton(enabled = ready, onClick = { if (running) InspectorAccessibilityService.instance?.stopInspector() else InspectorAccessibilityService.instance?.startInspector() }) { Text(if (running) "STOPPEN" else "SCHWEBEND STARTEN") }
            TextButton(enabled = ready, onClick = {
                if (InspectorAccessibilityService.instance?.capture() != true) Toast.makeText(context, "Keine zugängliche Ansicht", Toast.LENGTH_SHORT).show()
            }) { Text("ERFASSEN") }
        }
        Text(activity.ifBlank { "In einer App den schwebenden Layout-Knopf antippen." }, style = MaterialTheme.typography.bodySmall)
        Row {
            Text("${nodes.size} Elemente", Modifier.weight(1f).padding(top = 12.dp))
            TextButton(enabled = nodes.isNotEmpty(), onClick = { export = InspectorState.json(); exporter.launch("layout-inspector.json") }) { Text("JSON EXPORT") }
        }
        HorizontalDivider()
        LazyColumn(Modifier.weight(1f)) {
            items(nodes, key = { it.path }) { node ->
                Column(Modifier.fillMaxWidth().clickable { selected = node }.padding(start = (node.depth.coerceAtMost(10) * 12).dp, top = 8.dp, bottom = 8.dp)) {
                    Text(node.className.substringAfterLast('.'), color = if (node.visible) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(node.resourceId.ifBlank { node.text.ifBlank { node.path } }, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                }
            }
        }
    }
    selected?.let { node -> MtClassicAlertDialog(onDismissRequest = { selected = null }, title = { Text("Elementdetails") }, text = {
        Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState())) { Text(node.details()) }
    }, dismissButton = { TextButton(onClick = { context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Layout", node.details())) }) { Text("KOPIEREN") } }, confirmButton = { TextButton(onClick = { selected = null }) { Text("SCHLIESSEN") } }) }
}

private fun parseColor(value: String): Int? = runCatching {
    val raw = value.trim().removePrefix("#")
    val normalized = when (raw.length) { 3, 4 -> raw.map { "$it$it" }.joinToString(""); 6, 8 -> raw; else -> error("Ungültig") }
    require(normalized.all { it in "0123456789abcdefABCDEF" })
    android.graphics.Color.parseColor("#$normalized")
}.getOrNull()

@Composable
private fun ColorPickerContent(pick: Boolean, onChoose: (String) -> Unit) {
    val context = LocalContext.current
    val capture by ScreenColorPickerService.state.collectAsState()
    var color by remember { mutableIntStateOf(android.graphics.Color.rgb(33, 150, 243)) }
    var hex by remember { mutableStateOf("#2196F3") }
    val projectionManager = remember { context.getSystemService(MediaProjectionManager::class.java) }
    val projection = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            runCatching { ScreenColorPickerService.start(context, result.resultCode, result.data!!); (context as? Activity)?.moveTaskToBack(true) }
                .onFailure { Toast.makeText(context, it.message, Toast.LENGTH_LONG).show() }
        }
    }
    val overlayPermission = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { }
    LaunchedEffect(capture.argb, capture.sampled) { if (capture.sampled) { color = capture.argb; hex = capture.hex } }
    fun setChannel(index: Int, value: Int) {
        val channels = intArrayOf(android.graphics.Color.alpha(color), android.graphics.Color.red(color), android.graphics.Color.green(color), android.graphics.Color.blue(color))
        channels[index] = value
        color = android.graphics.Color.argb(channels[0], channels[1], channels[2], channels[3]); hex = String.format("#%08X", color)
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.fillMaxWidth().height(100.dp).background(Color(color)))
        OutlinedTextField(hex, { value -> hex = value; parseColor(value)?.let { color = it } }, singleLine = true, isError = parseColor(hex) == null, label = { Text("HEX • #RGB / #ARGB / #RRGGBB / #AARRGGBB") }, modifier = Modifier.fillMaxWidth())
        val values = intArrayOf(android.graphics.Color.alpha(color), android.graphics.Color.red(color), android.graphics.Color.green(color), android.graphics.Color.blue(color))
        listOf("Alpha", "Rot", "Grün", "Blau").forEachIndexed { index, label ->
            Text("$label: ${values[index]}", style = MaterialTheme.typography.labelMedium)
            Slider(value = values[index].toFloat(), onValueChange = { setChannel(index, it.toInt()) }, valueRange = 0f..255f, steps = 254)
        }
        Row {
            TextButton(enabled = parseColor(hex) != null, onClick = { context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Farbe", hex)) }) { Text("HEX KOPIEREN") }
            if (pick) Button(enabled = parseColor(hex) != null, onClick = { onChoose(hex) }) { Text("EINFÜGEN") }
        }
        HorizontalDivider()
        Text("Bildschirm-Pipette", style = MaterialTheme.typography.titleMedium)
        Text("Pipette über eine Farbe ziehen und loslassen. Android benötigt die Freigabe des gesamten Bildschirms und die Berechtigung zum Einblenden. Geschützte App-Inhalte können nicht erfasst werden.", style = MaterialTheme.typography.bodySmall)
        Button(onClick = {
            if (capture.running) ScreenColorPickerService.stop(context)
            else if (!Settings.canDrawOverlays(context)) overlayPermission.launch(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}")))
            else {
                val intent = if (Build.VERSION.SDK_INT >= 34) projectionManager.createScreenCaptureIntent(MediaProjectionConfig.createConfigForDefaultDisplay()) else projectionManager.createScreenCaptureIntent()
                projection.launch(intent)
            }
        }) { Text(if (capture.running) "PIPETTE STOPPEN" else if (!Settings.canDrawOverlays(context)) "EINBLENDEN ERLAUBEN" else "PIPETTE STARTEN") }
        capture.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}
