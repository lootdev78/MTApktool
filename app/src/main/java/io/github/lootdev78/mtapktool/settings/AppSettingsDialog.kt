package io.github.lootdev78.mtapktool.settings

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.lootdev78.mtapktool.BuildConfig
import io.github.lootdev78.mtapktool.apktool.ApktoolCliDialog
import io.github.lootdev78.mtapktool.apktool.ApktoolSettingsDialog
import io.github.lootdev78.mtapktool.apktool.SignatureManagerDialog
import io.github.lootdev78.mtapktool.archive.ArchiveFormat
import io.github.lootdev78.mtapktool.archive.ArchiveLevel
import io.github.lootdev78.mtapktool.archive.ArchiveSettings
import io.github.lootdev78.mtapktool.core.theme.ThemeManager
import io.github.lootdev78.mtapktool.core.theme.ThemeMode
import kotlinx.coroutines.launch

private enum class SettingsPage { ROOT, GENERAL, APKTOOL, SIGNATURE, ARCHIVE, CLI, FAQ, ABOUT }

private data class SettingsEntry(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val page: SettingsPage,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSettingsDialog(
    onDismiss: () -> Unit,
    onJobQueued: (String) -> Unit = {},
) {
    var page by remember { mutableStateOf(SettingsPage.ROOT) }
    var searchMode by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }

    if (page == SettingsPage.APKTOOL) {
        ApktoolSettingsDialog(onDismiss = { page = SettingsPage.ROOT })
        return
    }
    if (page == SettingsPage.SIGNATURE) {
        SignatureManagerDialog(onBack = { page = SettingsPage.ROOT })
        return
    }
    if (page == SettingsPage.CLI) {
        ApktoolCliDialog(onDismiss = { page = SettingsPage.ROOT }, onJobQueued = onJobQueued)
        return
    }
    if (page == SettingsPage.GENERAL) {
        GeneralSettingsDialog(onBack = { page = SettingsPage.ROOT })
        return
    }
    if (page == SettingsPage.ARCHIVE) {
        ArchiveDefaultsDialog(onBack = { page = SettingsPage.ROOT })
        return
    }
    if (page == SettingsPage.FAQ) {
        FaqDialog(onBack = { page = SettingsPage.ROOT })
        return
    }
    if (page == SettingsPage.ABOUT) {
        AboutDialog(onBack = { page = SettingsPage.ROOT })
        return
    }

    val entries = listOf(
        SettingsEntry("Anwendung", "Globale Anwendungseinstellungen konfigurieren.", Icons.Default.Settings, SettingsPage.GENERAL),
        SettingsEntry("Erstellen & Dekodieren", "Apktool für Erstellen, Dekodieren, Frameworks und AAPT2 konfigurieren.", Icons.Default.Build, SettingsPage.APKTOOL),
        SettingsEntry("Signatur", "Signaturdatei und APK-Signaturschemata konfigurieren.", Icons.Default.VpnKey, SettingsPage.SIGNATURE),
        SettingsEntry("Archivierung", "Format, Kompressionsstufe und Standardoptionen festlegen.", Icons.Default.Archive, SettingsPage.ARCHIVE),
        SettingsEntry("Apktool CLI", "Vollständige Apktool-Kommandos direkt als Job ausführen.", Icons.Default.Code, SettingsPage.CLI),
        SettingsEntry("FAQ", "Hilfe zur Benutzung des Programms", Icons.Default.HelpOutline, SettingsPage.FAQ),
        SettingsEntry("Über", "Informationen über App, Runtime und integriertes Apktool", Icons.Default.Info, SettingsPage.ABOUT),
    )
    val filtered = entries.filter {
        query.isBlank() || it.title.contains(query, ignoreCase = true) || it.subtitle.contains(query, ignoreCase = true)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = true),
    ) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text("Einstellungen") },
                        navigationIcon = {
                            IconButton(onClick = onDismiss) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück") }
                        },
                        actions = {
                            IconButton(onClick = { searchMode = !searchMode; if (!searchMode) query = "" }) {
                                Icon(Icons.Default.Search, contentDescription = "Suchen")
                            }
                        },
                    )
                },
            ) { insets ->
                Column(Modifier.fillMaxSize().padding(insets)) {
                    if (searchMode) {
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            placeholder = { Text("Einstellungen durchsuchen") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(filtered, key = { it.title }) { entry ->
                            SettingsRow(entry) { page = entry.page }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsRow(entry: SettingsEntry, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(entry.icon, contentDescription = null, modifier = Modifier.size(30.dp))
        Spacer(Modifier.width(20.dp))
        Column(Modifier.weight(1f)) {
            Text(entry.title, style = MaterialTheme.typography.titleLarge)
            Text(entry.subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun GeneralSettingsDialog(onBack: () -> Unit) {
    ExplorerPreferencesDialog(onBack = onBack)
}

@Composable
private fun ArchiveDefaultsDialog(onBack: () -> Unit) {
    val context = LocalContext.current
    var defaults by remember { mutableStateOf(ArchiveSettings.load(context)) }
    var formatMenu by remember { mutableStateOf(false) }
    var levelMenu by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onBack,
        title = { Text("Archivierung") },
        text = {
            Column {
                Text("Format", style = MaterialTheme.typography.labelLarge)
                TextButton(onClick = { formatMenu = true }, modifier = Modifier.fillMaxWidth()) { Text(defaults.format.label) }
                DropdownMenu(expanded = formatMenu, onDismissRequest = { formatMenu = false }) {
                    ArchiveFormat.entries.forEach { f ->
                        DropdownMenuItem(text = { Text(f.label) }, onClick = { defaults = defaults.copy(format = f); formatMenu = false })
                    }
                }
                Text("Level", style = MaterialTheme.typography.labelLarge)
                TextButton(onClick = { levelMenu = true }, modifier = Modifier.fillMaxWidth()) { Text(defaults.level.label) }
                DropdownMenu(expanded = levelMenu, onDismissRequest = { levelMenu = false }) {
                    ArchiveLevel.entries.forEach { l ->
                        DropdownMenuItem(text = { Text(l.label) }, onClick = { defaults = defaults.copy(level = l); levelMenu = false })
                    }
                }
                OutlinedTextField(
                    value = defaults.splitLengthMb.takeIf { it > 0 }?.toString().orEmpty(),
                    onValueChange = { defaults = defaults.copy(splitLengthMb = it.filter(Char::isDigit).toLongOrNull() ?: 0L) },
                    label = { Text("Standard Split-Länge (MB, 0 = aus)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                ToggleText("Einträge einzeln komprimieren", defaults.compressEachIndependently) { defaults = defaults.copy(compressEachIndependently = it) }
                ToggleText("Quelldateien nach Erfolg löschen", defaults.deleteSourcesAfterCompression) { defaults = defaults.copy(deleteSourcesAfterCompression = it) }
                ToggleText("In das andere Panel komprimieren", defaults.compressToOtherPane) { defaults = defaults.copy(compressToOtherPane = it) }
            }
        },
        dismissButton = { TextButton(onClick = onBack) { Text("ABBRECHEN") } },
        confirmButton = {
            TextButton(onClick = { ArchiveSettings.save(context, defaults); onBack() }) { Text("SPEICHERN") }
        },
    )
}

@Composable
private fun ToggleText(label: String, value: Boolean, set: (Boolean) -> Unit) {
    TextButton(onClick = { set(!value) }, modifier = Modifier.fillMaxWidth()) { Text(if (value) "✓  $label" else label) }
}

@Composable
private fun FaqDialog(onBack: () -> Unit) {
    AlertDialog(
        onDismissRequest = onBack,
        title = { Text("FAQ") },
        text = {
            Text(
                "APK dekompilieren: APK im Dateifenster antippen, Optionen wählen und OK drücken. Der Auftrag erscheint rechts im Task-Panel.\n\n" +
                    "APK bauen: Einen Ordner mit apktool.yml öffnen und 'Dieses Projekt kompilieren' wählen.\n\n" +
                    "Task-Panel: Auf der unteren Navigation vom rechten Rand zur Mitte wischen.\n\n" +
                    "Jump to path: Auf den blauen Pfad eines Panels tippen oder den Pfeil unten verwenden."
            )
        },
        confirmButton = { TextButton(onClick = onBack) { Text("ZURÜCK") } },
    )
}

@Composable
private fun AboutDialog(onBack: () -> Unit) {
    AlertDialog(
        onDismissRequest = onBack,
        title = { Text("Über") },
        text = {
            Text(
                "MTApktool ${BuildConfig.VERSION_NAME}\n\n" +
                    "Integrierte Funktionen: Apktool Decode/Build, Framework-Verwaltung, AAPT2, Signierung, Job-Runner, Dual-Panel-Dateimanager und Archivierung."
            )
        },
        confirmButton = { TextButton(onClick = onBack) { Text("ZURÜCK") } },
    )
}
