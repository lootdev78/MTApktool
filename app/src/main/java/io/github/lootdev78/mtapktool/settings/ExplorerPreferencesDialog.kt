package io.github.lootdev78.mtapktool.settings

import android.content.Context
import android.os.Environment
import android.provider.DocumentsContract
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.lootdev78.mtapktool.apktool.ApktoolSettings
import io.github.lootdev78.mtapktool.core.theme.ThemeManager
import io.github.lootdev78.mtapktool.core.theme.ThemeMode
import kotlinx.coroutines.launch
import java.io.File

private data class Choice(val key: String, val label: String)
private data class SortableChoice(val key: String, val label: String, val icon: ImageVector)

private val accentChoices = listOf(
    "black" to Color(0xFF111111),
    "true_black" to Color(0xFF000000),
    "gray" to Color(0xFF444444),
    "cyan" to Color(0xFF0D7BA8),
    "blue" to Color(0xFF1976C8),
    "indigo" to Color(0xFF3949AB),
    "brown" to Color(0xFFA26754),
    "pink" to Color(0xFFA94769),
    "purple" to Color(0xFF5C4695),
    "lime" to Color(0xFF5A8238),
    "green" to Color(0xFF266C2D),
    "teal_dark" to Color(0xFF00695C),
    "monet_teal" to Color(0xFF00838F),
    "monet_cyan" to Color(0xFF008A95),
    "monet_blue" to Color(0xFF4B70A4),
)

private val fileMenuLabels = mapOf(
    "copy" to "Kopieren ->",
    "move" to "Verschieben ->",
    "delete" to "Löschen",
    "rename" to "Umbenennen",
    "tools" to "Tools",
    "compress" to "Komprimieren",
    "properties" to "Eigenschaften",
    "share" to "Teilen",
    "open_with" to "Öffnen mit…",
    "bookmark" to "Lesezeichen…",
)

private val builtInLabels = mapOf(
    "text" to SortableChoice("text", "Texteditor", Icons.Default.Description),
    "archive" to SortableChoice("archive", "Archivviewer", Icons.Default.Archive),
    "apk" to SortableChoice("apk", "APK-Viewer", Icons.Default.Android),
    "split" to SortableChoice("split", "APKS-Funktionen", Icons.Default.Android),
    "apktool" to SortableChoice("apktool", "Apktool", Icons.Default.Build),
    "reveal" to SortableChoice("reveal", "Im Panel anzeigen", Icons.Default.LocationOn),
    "external" to SortableChoice("external", "Andere App", Icons.Default.FolderOpen),
)

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ExplorerPreferencesDialog(onBack: () -> Unit) {
    val context = LocalContext.current
    ExplorerPreferences.init(context)
    val prefs by ExplorerPreferences.state.collectAsState()
    val themeManager = remember(context) { ThemeManager(context.applicationContext) }
    val themeMode by themeManager.themeModeFlow.collectAsState(initial = ThemeMode.SYSTEM)
    val scope = rememberCoroutineScope()

    var themeModeDialog by remember { mutableStateOf(false) }
    var accentDialog by remember { mutableStateOf(false) }
    var fileSizeDialog by remember { mutableStateOf(false) }
    var maxLinesDialog by remember { mutableStateOf(false) }
    var fileTimeDialog by remember { mutableStateOf(false) }
    var dateFormatDialog by remember { mutableStateOf(false) }
    var fileMenuDialog by remember { mutableStateOf(false) }
    var builtInDialog by remember { mutableStateOf(false) }
    var workspaceDialog by remember { mutableStateOf(false) }
    var startupLeftDialog by remember { mutableStateOf(false) }
    var startupRightDialog by remember { mutableStateOf(false) }
    var autoCleanDialog by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onBack,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = true),
    ) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text("Preferences") },
                        navigationIcon = {
                            IconButton(onClick = onBack) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
                            }
                        },
                    )
                },
            ) { insets ->
                LazyColumn(Modifier.fillMaxSize().padding(insets)) {
                    item { SectionTitle("Startup") }
                    item { PreferenceSwitch("Root-Berechtigung beim Start anfragen", "Beim Start optional Root anfragen.", prefs.requestRootAtStartup) { update(context) { copy(requestRootAtStartup = it) } } }
                    item { PreferenceSwitch("Shell-Berechtigung beim Start anfragen", "Beim Start optional Shell-Berechtigung anfragen.", prefs.requestShellAtStartup) { update(context) { copy(requestShellAtStartup = it) } } }
                    item { PreferenceValue("Startpfad – linkes Fenster", startupLabel(prefs.startupLeft)) { startupLeftDialog = true } }
                    item { PreferenceValue("Startpfad – rechtes Fenster", startupLabel(prefs.startupRight)) { startupRightDialog = true } }

                    item { Divider() }
                    item { SectionTitle("Darstellung") }
                    item { PreferenceValue("Theme", when (themeMode) { ThemeMode.SYSTEM -> "System"; ThemeMode.LIGHT -> "Hell"; ThemeMode.DARK -> "Dunkel" }) { themeModeDialog = true } }
                    item { PreferenceValue("Theme color", "Farbschema der Oberfläche auswählen.") { accentDialog = true } }
                    item { PreferenceValue("File list size", when (prefs.fileListSize) { "medium" -> "Medium"; "big" -> "Big"; else -> "Small" }) { fileSizeDialog = true } }
                    item { PreferenceValue("Max lines of file name", prefs.maxFileNameLines.toString()) { maxLinesDialog = true } }
                    item { PreferenceValue("File list time preference", if (prefs.fileListTimePreference == "full") "Show full date and seconds" else "Hide seconds, simplified year") { fileTimeDialog = true } }
                    item { PreferenceSwitch("Disable permission in file list", "Dateirechte in der Liste nicht anzeigen.", prefs.disablePermissionInFileList) { update(context) { copy(disablePermissionInFileList = it) } } }
                    item { PreferenceValue("Date time format", prefs.dateTimeFormat) { dateFormatDialog = true } }

                    item { Divider() }
                    item { SectionTitle("General") }
                    item { PreferenceSwitch("Generate backup file", "Beim Speichern im Texteditor die Originaldatei als .bak sichern.", prefs.generateBackupFile) {
                        update(context) { copy(generateBackupFile = it) }
                        context.getSharedPreferences("editor_pref", Context.MODE_PRIVATE).edit().putBoolean("generate_backup_file", it).apply()
                    } }
                    item { PreferenceSwitch("Preserve file time", "Änderungszeit beim Kopieren/Extrahieren möglichst erhalten.", prefs.preserveFileTime) { update(context) { copy(preserveFileTime = it) } } }
                    item { PreferenceValue("Sort file menu", "Nach langem Drücken sortieren.") { fileMenuDialog = true } }
                    item { PreferenceValue("Sort built-in opening method", "Nur MTApktool-eigene Öffnungsmethoden.") { builtInDialog = true } }
                    item { PreferenceValue("Custom MTApktool directory", prefs.customWorkspace) { workspaceDialog = true } }

                    item { Divider() }
                    item { SectionTitle("Recycle Bin") }
                    item { PreferenceSwitch("Enable recycle bin feature", "Gelöschte lokale Dateien können zuerst in den Papierkorb verschoben werden.", prefs.recycleBinEnabled) { update(context) { copy(recycleBinEnabled = it) } } }
                    item { PreferenceSwitch("Move to recycle bin by default", "Beim Löschen standardmäßig in den Papierkorb verschieben.", prefs.moveToRecycleBinByDefault, enabled = prefs.recycleBinEnabled) { update(context) { copy(moveToRecycleBinByDefault = it) } } }
                    item { PreferenceValue("Automatically clean recycle bin files", if (prefs.autoCleanRecycleBinDays <= 0) "Disable" else "${prefs.autoCleanRecycleBinDays} Tage", enabled = prefs.recycleBinEnabled) { autoCleanDialog = true } }
                    item { PreferenceSwitch("Show deletion warning", "Warnung vor endgültigem Löschen anzeigen.", prefs.showDeletionWarning) { update(context) { copy(showDeletionWarning = it) } } }

                    item { Divider() }
                    item { SectionTitle("Installation") }
                    item { PreferenceSwitch("APK installation verification", "Signatur und Versionscode vor Installation prüfen.", prefs.apkInstallationVerification) { update(context) { copy(apkInstallationVerification = it) } } }

                    item { Divider() }
                    item { SectionTitle("External storage") }
                    item { PreferenceSwitch("Load thumbnails from external storage", "Thumbnails auf SAF/USB-Speichern laden.", prefs.loadExternalThumbnails) { update(context) { copy(loadExternalThumbnails = it) } } }
                    item { PreferenceSwitch("Optimize external storage data transfer", "Größere Puffer für Kopieren/Verschieben auf SAF/USB verwenden.", prefs.optimizeExternalTransfer) { update(context) { copy(optimizeExternalTransfer = it) } } }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
    }

    if (themeModeDialog) {
        RadioChoiceDialog(
            title = "Theme",
            choices = listOf(Choice("SYSTEM", "System"), Choice("LIGHT", "Hell"), Choice("DARK", "Dunkel")),
            selected = themeMode.name,
            onDismiss = { themeModeDialog = false },
        ) { key -> scope.launch { themeManager.setThemeMode(ThemeMode.valueOf(key)) }; themeModeDialog = false }
    }
    if (accentDialog) ThemeColorDialog(prefs.accentKey, { accentDialog = false }) { key -> update(context) { copy(accentKey = key) }; accentDialog = false }
    if (fileSizeDialog) RadioChoiceDialog("File list size", listOf(Choice("small", "Small"), Choice("medium", "Medium"), Choice("big", "Big")), prefs.fileListSize, { fileSizeDialog = false }) { key -> update(context) { copy(fileListSize = key) }; fileSizeDialog = false }
    if (maxLinesDialog) RadioChoiceDialog("Max lines of file name", (1..8).map { Choice(it.toString(), it.toString()) }, prefs.maxFileNameLines.toString(), { maxLinesDialog = false }) { key -> update(context) { copy(maxFileNameLines = key.toInt()) }; maxLinesDialog = false }
    if (fileTimeDialog) RadioChoiceDialog("File list time preference", listOf(Choice("hide_seconds_simplified_year", "Hide seconds, simplified year"), Choice("full", "Show full date and seconds")), prefs.fileListTimePreference, { fileTimeDialog = false }) { key -> update(context) { copy(fileListTimePreference = key) }; fileTimeDialog = false }
    if (dateFormatDialog) RadioChoiceDialog("Date time format", listOf(Choice("yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd HH:mm:ss"), Choice("dd-MM-yyyy HH:mm:ss", "dd-MM-yyyy HH:mm:ss"), Choice("HH:mm:ss dd-MM-yyyy", "HH:mm:ss dd-MM-yyyy")), prefs.dateTimeFormat, { dateFormatDialog = false }) { key -> update(context) { copy(dateTimeFormat = key) }; dateFormatDialog = false }
    if (startupLeftDialog) StartupDialog("Startup path - left window", prefs.startupLeft, { startupLeftDialog = false }) { update(context) { copy(startupLeft = it) }; startupLeftDialog = false }
    if (startupRightDialog) StartupDialog("Startup path - right window", prefs.startupRight, { startupRightDialog = false }) { update(context) { copy(startupRight = it) }; startupRightDialog = false }
    if (fileMenuDialog) {
        SortGridDialog(
            title = "Sort file menu",
            initial = prefs.fileMenuOrder,
            columns = 2,
            labelFor = { fileMenuLabels[it] ?: it },
            iconFor = { Icons.Default.SwapHoriz },
            onDismiss = { fileMenuDialog = false },
            onSave = { order -> update(context) { copy(fileMenuOrder = order) }; fileMenuDialog = false },
        )
    }
    if (builtInDialog) {
        SortGridDialog(
            title = "Sort built-in opening method",
            initial = prefs.builtInOpenOrder,
            columns = 3,
            labelFor = { builtInLabels[it]?.label ?: it },
            iconFor = { builtInLabels[it]?.icon ?: Icons.Default.FolderOpen },
            onDismiss = { builtInDialog = false },
            onSave = { order -> update(context) { copy(builtInOpenOrder = order) }; builtInDialog = false },
        )
    }
    if (workspaceDialog) WorkspaceDialog(prefs.customWorkspace, { workspaceDialog = false }) { path ->
        update(context) { copy(customWorkspace = path) }
        val root = File(path)
        ApktoolSettings.savePathsAndWorkers(
            context,
            ApktoolSettings.maxWorkers(context),
            File(root, "projects").absolutePath,
            File(root, "output").absolutePath,
            ApktoolSettings.apktoolThreads(context),
        )
        workspaceDialog = false
    }
    if (autoCleanDialog) RadioChoiceDialog(
        "Automatically clean recycle bin files",
        listOf(Choice("0", "Disable"), Choice("1", "1 Tag"), Choice("7", "7 Tage"), Choice("30", "30 Tage"), Choice("90", "90 Tage")),
        prefs.autoCleanRecycleBinDays.toString(),
        { autoCleanDialog = false },
    ) { key -> update(context) { copy(autoCleanRecycleBinDays = key.toInt()) }; autoCleanDialog = false }
}

private fun update(context: Context, transform: ExplorerPrefs.() -> ExplorerPrefs) =
    ExplorerPreferences.update(context) { it.transform() }

private fun startupLabel(key: String) = if (key == "last") "Last path" else "Home"

@Composable
private fun SectionTitle(title: String) {
    Text(
        title,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp),
    )
}

@Composable
private fun PreferenceValue(title: String, subtitle: String, enabled: Boolean = true, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onClick).padding(horizontal = 18.dp, vertical = 13.dp),
    ) {
        Text(title, fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.38f))
        Spacer(Modifier.height(2.dp))
        Text(subtitle, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 0.82f else 0.34f))
    }
}

@Composable
private fun PreferenceSwitch(title: String, subtitle: String, checked: Boolean, enabled: Boolean = true, onChecked: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(enabled = enabled) { onChecked(!checked) }.padding(horizontal = 18.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.38f))
            Spacer(Modifier.height(2.dp))
            Text(subtitle, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 0.82f else 0.34f))
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, enabled = enabled, onCheckedChange = onChecked)
    }
}

@Composable
private fun RadioChoiceDialog(title: String, choices: List<Choice>, selected: String, onDismiss: () -> Unit, onSelect: (String) -> Unit) {
    AlertDialog(
        shape = RoundedCornerShape(2.dp),
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                choices.forEach { choice ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onSelect(choice.key) }.padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = selected == choice.key, onClick = { onSelect(choice.key) })
                        Spacer(Modifier.width(10.dp))
                        Text(choice.label, fontSize = 18.sp)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("CANCEL") } },
    )
}

@Composable
private fun StartupDialog(title: String, selected: String, onDismiss: () -> Unit, onSelect: (String) -> Unit) =
    RadioChoiceDialog(title, listOf(Choice("home", "Home"), Choice("last", "Last path")), selected, onDismiss, onSelect)

@Composable
private fun ThemeColorDialog(selected: String, onDismiss: () -> Unit, onSelect: (String) -> Unit) {
    AlertDialog(
        shape = RoundedCornerShape(2.dp),
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
        onDismissRequest = onDismiss,
        title = { Text("Theme color") },
        text = {
            Column {
                accentChoices.chunked(3).forEachIndexed { rowIndex, row ->
                    if (rowIndex == 4) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Divider(Modifier.weight(1f))
                            Text("Monet Colors", modifier = Modifier.padding(horizontal = 12.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Divider(Modifier.weight(1f))
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { (key, color) ->
                            Box(
                                Modifier.weight(1f).height(54.dp).clip(RoundedCornerShape(22.dp)).background(color)
                                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f), RoundedCornerShape(22.dp))
                                    .clickable { onSelect(key) },
                                contentAlignment = Alignment.Center,
                            ) {
                                if (key == selected) Text("✓", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("CLOSE") } },
    )
}

@Composable
private fun SortGridDialog(
    title: String,
    initial: List<String>,
    columns: Int,
    labelFor: (String) -> String,
    iconFor: (String) -> ImageVector,
    onDismiss: () -> Unit,
    onSave: (List<String>) -> Unit,
) {
    var order by remember(initial) { mutableStateOf(initial) }
    AlertDialog(
        shape = RoundedCornerShape(2.dp),
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text("Lange drücken und ziehen, um die Reihenfolge zu ändern.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                Spacer(Modifier.height(10.dp))
                order.chunked(columns).forEachIndexed { rowIndex, row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEachIndexed { colIndex, key ->
                            val index = rowIndex * columns + colIndex
                            SortableCell(
                                label = labelFor(key),
                                icon = iconFor(key),
                                modifier = Modifier.weight(1f),
                                onMove = { dx, dy ->
                                    val target = when {
                                        kotlin.math.abs(dx) > kotlin.math.abs(dy) && dx > 0 -> index + 1
                                        kotlin.math.abs(dx) > kotlin.math.abs(dy) && dx < 0 -> index - 1
                                        dy > 0 -> index + columns
                                        else -> index - columns
                                    }.coerceIn(0, order.lastIndex)
                                    if (target != index) {
                                        val list = order.toMutableList()
                                        val moved = list.removeAt(index)
                                        list.add(target, moved)
                                        order = list
                                    }
                                },
                            )
                        }
                        repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                    Spacer(Modifier.height(6.dp))
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("CLOSE") } },
        confirmButton = { TextButton(onClick = { onSave(order) }) { Text("OK") } },
    )
}

@Composable
private fun SortableCell(label: String, icon: ImageVector, modifier: Modifier, onMove: (Float, Float) -> Unit) {
    var totalX by remember { mutableFloatStateOf(0f) }
    var totalY by remember { mutableFloatStateOf(0f) }
    Column(
        modifier.padding(4.dp).pointerInput(label) {
            detectDragGesturesAfterLongPress(
                onDragStart = { totalX = 0f; totalY = 0f },
                onDrag = { change, amount ->
                    change.consume()
                    totalX += amount.x
                    totalY += amount.y
                    if (kotlin.math.abs(totalX) > 70f || kotlin.math.abs(totalY) > 70f) {
                        onMove(totalX, totalY)
                        totalX = 0f
                        totalY = 0f
                    }
                },
            )
        },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(52.dp)) {
            Box(contentAlignment = Alignment.Center) { Icon(icon, null, modifier = Modifier.size(28.dp)) }
        }
        Spacer(Modifier.height(4.dp))
        Text(label, fontSize = 12.sp, maxLines = 2)
    }
}

@Composable
private fun WorkspaceDialog(initial: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    val context = LocalContext.current
    var path by remember(initial) { mutableStateOf(initial) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val resolved = runCatching {
            val id = DocumentsContract.getTreeDocumentId(uri)
            when {
                id.startsWith("primary:") -> File(Environment.getExternalStorageDirectory(), id.substringAfter(':')).absolutePath
                else -> null
            }
        }.getOrNull()
        if (resolved != null) path = resolved
        else Toast.makeText(context, "Nur direkt auflösbare lokale Ordner können als Apktool-Arbeitsverzeichnis verwendet werden.", Toast.LENGTH_LONG).show()
    }
    AlertDialog(
        shape = RoundedCornerShape(2.dp),
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
        onDismissRequest = onDismiss,
        title = { Text("Custom MTApktool directory") },
        text = {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(path, { path = it }, modifier = Modifier.weight(1f), singleLine = true)
                Spacer(Modifier.width(8.dp))
                IconButton(onClick = { picker.launch(null) }) { Icon(Icons.Default.MoreHoriz, null) }
            }
        },
        dismissButton = {
            TextButton(onClick = { path = Environment.getExternalStorageDirectory().resolve("apktool").absolutePath }) { Text("RESET") }
        },
        confirmButton = {
            Row {
                TextButton(onClick = onDismiss) { Text("CANCEL") }
                TextButton(onClick = { if (path.isNotBlank()) onSave(path) }) { Text("OK") }
            }
        },
    )
}
