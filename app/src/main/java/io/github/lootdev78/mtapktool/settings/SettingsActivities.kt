package io.github.lootdev78.mtapktool.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import io.github.lootdev78.mtapktool.BuildConfig
import io.github.lootdev78.mtapktool.apktool.ApktoolBuildDefaults
import io.github.lootdev78.mtapktool.apktool.ApktoolDecodeDefaults
import io.github.lootdev78.mtapktool.apktool.ApktoolGeneralDefaults
import io.github.lootdev78.mtapktool.apktool.ApktoolJobService
import io.github.lootdev78.mtapktool.apktool.ApktoolSettings
import io.github.lootdev78.mtapktool.apktool.ApktoolSignatureDefaults
import io.github.lootdev78.mtapktool.apktool.ApkModulePreferences
import io.github.lootdev78.mtapktool.archive.ArchiveFormat
import io.github.lootdev78.mtapktool.archive.ArchiveLevel
import io.github.lootdev78.mtapktool.archive.ArchiveSettings
import io.github.lootdev78.mtapktool.core.theme.MTApktoolTheme
import io.github.lootdev78.mtapktool.core.theme.ThemeManager
import io.github.lootdev78.mtapktool.core.theme.ThemeMode
import io.github.lootdev78.mtapktool.feature.explorer.model.CustomLocationStore
import io.github.lootdev78.mtapktool.tools.ApkExtractorPreferences
import io.github.lootdev78.mtapktool.tools.ApkClonerPreferences
import io.github.lootdev78.mtapktool.tools.ApkExtractorOptions
import io.github.lootdev78.mtapktool.tools.ApkClonerOptions
import kotlinx.coroutines.launch
import java.io.File
import org.json.JSONArray

private fun ComponentActivity.composePage(content: @Composable () -> Unit) {
    setContent { MTApktoolTheme { content() } }
}

class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); composePage { SettingsHome(::finish, ::startActivity) } }
}
class ApplicationSettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); composePage { ApplicationSettingsPage(::finish) } }
}
class ApktoolSettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); composePage { ApktoolSettingsPage(::finish) } }
}
class SignatureSettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); composePage { SignatureSettingsPage(::finish) } }
}
class ArchiveSettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); composePage { ArchiveSettingsPage(::finish) } }
}
class FrameworkSettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); composePage { FrameworkSettingsPage(::finish) } }
}
class Aapt2SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); composePage { Aapt2SettingsPage(::finish) } }
}
class ApkModulesSettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); composePage { ApkModulesSettingsPage(::finish) } }
}
class ApkExtractorSettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); composePage { ApkExtractorSettingsPage(::finish) } }
}
class ApkClonerSettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); composePage { ApkClonerSettingsPage(::finish) } }
}
class TextEditorSettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); composePage { TextEditorSettingsPage(::finish) } }
}
class ApktoolCliActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); composePage { ApktoolCliPage(::finish) } }
}
class FaqActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); composePage { SimpleTextPage("FAQ", ::finish, "APK antippen → APK-Info. APKS/APKM/XAPK/APKX antippen → Container-Funktionen.\n\nDekompilieren/Build und Apktool-CLI laufen als Jobs im separaten :apktool-Prozess.\n\nArchive lassen sich direkt durchsuchen und in das aktuelle/andere Panel entpacken.\n\nNeue Speicherorte werden über den Android Document Tree Picker hinzugefügt.") } }
}
class AboutActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); composePage { SimpleTextPage("Über", ::finish, "MTApktool ${BuildConfig.VERSION_NAME}\n\nDual-Panel Explorer, Apktool, Split-Paket-Installer, APKS/APKM/XAPK/APKX Werkzeuge, zipalign, Signierung und Archive.") } }
}

private data class PageEntry(val title: String, val subtitle: String, val icon: ImageVector, val activity: Class<out ComponentActivity>)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsHome(onBack: () -> Unit, start: (Intent) -> Unit) {
    val context = LocalContext.current
    var searching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    val pages = listOf(
        PageEntry("Anwendung", "Globale Anwendungseinstellungen konfigurieren.", Icons.Default.Settings, ApplicationSettingsActivity::class.java),
        PageEntry("Erstellen & Dekodieren", "Apktool für Erstellen, Dekodieren, Frameworks und AAPT2 konfigurieren.", Icons.Default.Build, ApktoolSettingsActivity::class.java),
        PageEntry("Signatur", "Signaturdatei und APK-Signaturschemata konfigurieren.", Icons.Default.VpnKey, SignatureSettingsActivity::class.java),
        PageEntry("Archivierung", "Format, Kompressionsstufe und Standardoptionen festlegen.", Icons.Default.Archive, ArchiveSettingsActivity::class.java),
        PageEntry("APK-Module", "AntiSplit-M/APKS→APK, APKX→APK und zipalign konfigurieren.", Icons.Default.Tune, ApkModulesSettingsActivity::class.java),
        PageEntry("APK Extractor", "Installierte APKs/APKS extrahieren; Standardziel /apktool/apks.", Icons.Default.Archive, ApkExtractorSettingsActivity::class.java),
        PageEntry("APK Cloner", "Paketnamen-Klonen und Standardausgabe konfigurieren.", Icons.Default.Tune, ApkClonerSettingsActivity::class.java),
        PageEntry("Text Editor", "Eingebetteten MH-TextEditor und Anzeigeoptionen konfigurieren.", Icons.Default.Code, TextEditorSettingsActivity::class.java),
        PageEntry("Frameworks", "Framework-Verzeichnis und Standard-Framework konfigurieren.", Icons.Default.FolderOpen, FrameworkSettingsActivity::class.java),
        PageEntry("AAPT2", "Mitgelieferte oder benutzerdefinierte AAPT2 Variante wählen.", Icons.Default.Code, Aapt2SettingsActivity::class.java),
        PageEntry("Apktool CLI", "Vollständige Apktool-Kommandos direkt als Job ausführen.", Icons.Default.Code, ApktoolCliActivity::class.java),
        PageEntry("FAQ", "Hilfe zur Benutzung des Programms", Icons.Default.HelpOutline, FaqActivity::class.java),
        PageEntry("Über", "Informationen über App, Runtime und integriertes Apktool", Icons.Default.Info, AboutActivity::class.java),
    )
    val filtered = pages.filter { query.isBlank() || it.title.contains(query, true) || it.subtitle.contains(query, true) }
    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Einstellungen") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück") } },
            actions = { IconButton(onClick = { searching = !searching; if (!searching) query = "" }) { Icon(Icons.Default.Search, "Suchen") } },
        )
    }) { insets ->
        Column(Modifier.fillMaxSize().padding(insets)) {
            if (searching) OutlinedTextField(query, { query = it }, placeholder = { Text("Einstellungen durchsuchen") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(12.dp))
            LazyColumn(Modifier.fillMaxSize()) {
                items(filtered, key = { it.title }) { entry ->
                    SettingsRow(entry.icon, entry.title, entry.subtitle) { start(Intent(context, entry.activity)) }
                }
            }
        }
    }
}

@Composable
private fun SettingsRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 22.dp, vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(34.dp))
        Spacer(Modifier.width(22.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineSmall)
            Text(subtitle, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PageScaffold(title: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    Scaffold(topBar = { TopAppBar(title = { Text(title) }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück") } }) }) { insets ->
        LazyColumn(Modifier.fillMaxSize().padding(insets)) { item { Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) { content() } } }
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String = "", value: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(enabled = enabled) { onChange(!value) }.padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.Medium); if (subtitle.isNotBlank()) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        Switch(checked = value, onCheckedChange = onChange, enabled = enabled)
    }
}

@Composable
private fun ValueRow(title: String, value: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.Medium); Text(value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis) }
    }
}

@Composable
private fun ApplicationSettingsPage(onBack: () -> Unit) {
    val context = LocalContext.current
    val manager = remember(context) { ThemeManager(context.applicationContext) }
    val mode by manager.themeModeFlow.collectAsState(initial = ThemeMode.SYSTEM)
    val scope = rememberCoroutineScope()
    var locationCount by remember { mutableStateOf(CustomLocationStore.all(context).size) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }
            CustomLocationStore.add(context, uri)
            locationCount = CustomLocationStore.all(context).size
        }
    }
    PageScaffold("Anwendung", onBack) {
        Text("Darstellung", modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp), style = MaterialTheme.typography.titleMedium)
        ThemeMode.entries.forEach { m ->
            val label = when (m) { ThemeMode.SYSTEM -> "Systemstandard"; ThemeMode.LIGHT -> "Hell"; ThemeMode.DARK -> "Dunkel" }
            Row(Modifier.fillMaxWidth().clickable { scope.launch { manager.setThemeMode(m) } }.padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = mode == m, onClick = { scope.launch { manager.setThemeMode(m) } }); Text(label)
            }
        }
        Text("Speicherorte", modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp), style = MaterialTheme.typography.titleMedium)
        ValueRow("Speicherort hinzufügen", "$locationCount hinzugefügte Document-Tree Speicherorte") { picker.launch(null) }
    }
}

@Composable
private fun ApktoolSettingsPage(onBack: () -> Unit) {
    val context = LocalContext.current
    var general by remember { mutableStateOf(ApktoolSettings.generalDefaults(context)) }
    var decode by remember { mutableStateOf(ApktoolSettings.decodeDefaults(context)) }
    var build by remember { mutableStateOf(ApktoolSettings.buildDefaults(context)) }
    fun saveG(v: ApktoolGeneralDefaults) { general = v; ApktoolSettings.saveGeneralDefaults(context, v) }
    fun saveD(v: ApktoolDecodeDefaults) { decode = v; ApktoolSettings.saveDecodeDefaults(context, v) }
    fun saveB(v: ApktoolBuildDefaults) { build = v; ApktoolSettings.saveBuildDefaults(context, v) }
    PageScaffold("Erstellen & Dekodieren", onBack) {
        SwitchRow("Nach Abschluss benachrichtigen", value = general.notifyOnCompletion) { saveG(general.copy(notifyOnCompletion = it)) }
        SwitchRow("Dekompilierte Sources", "Klassen*.dex zu Smali dekompilieren", !decode.noSources) { saveD(decode.copy(noSources = !it, allSources = if (it) decode.allSources else false, noDebugInfo = if (it) decode.noDebugInfo else false, useRegisters = if (it) decode.useRegisters else false)) }
        SwitchRow("Alle DEX", "Alle classes*.dex verarbeiten", decode.allSources, !decode.noSources) { saveD(decode.copy(allSources = it)) }
        SwitchRow("Debug-Informationen", value = !decode.noDebugInfo, enabled = !decode.noSources) { saveD(decode.copy(noDebugInfo = !it)) }
        SwitchRow("Register statt Lokale", value = decode.useRegisters, enabled = !decode.noSources) { saveD(decode.copy(useRegisters = it)) }
        SwitchRow("Ressourcen dekompilieren", value = !decode.noResources) { saveD(decode.copy(noResources = !it, onlyManifest = if (it) decode.onlyManifest else false)) }
        SwitchRow("Nur AndroidManifest.xml", value = decode.onlyManifest, enabled = !decode.noResources) { saveD(decode.copy(onlyManifest = it)) }
        SwitchRow("Gebrochene Ressourcen behalten", value = decode.keepBrokenResources, enabled = !decode.noResources && !decode.onlyManifest) { saveD(decode.copy(keepBrokenResources = it)) }
        SwitchRow("Original anpassen (--match-original)", value = decode.matchOriginal) { saveD(decode.copy(matchOriginal = it)) }
        SwitchRow("Vollständigen Build erzwingen", value = build.force) { saveB(build.copy(force = it)) }
        SwitchRow("Resource-Crunching deaktivieren", value = build.noCrunch) { saveB(build.copy(noCrunch = it)) }
        SwitchRow("Originaldateien kopieren", value = build.copyOriginal) { saveB(build.copy(copyOriginal = it)) }
        SwitchRow("Zipalign nach Build", value = build.zipalign) { saveB(build.copy(zipalign = it)) }
        SwitchRow("Signieren nach Build", value = build.sign) { saveB(build.copy(sign = it)) }
        val projects = remember { mutableStateOf(ApktoolSettings.projectsRoot(context)) }
        val output = remember { mutableStateOf(ApktoolSettings.outputRoot(context)) }
        OutlinedTextField(projects.value, { projects.value = it; ApktoolSettings.savePathsAndWorkers(context, ApktoolSettings.maxWorkers(context), it, output.value) }, label = { Text("Projects root") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp))
        OutlinedTextField(output.value, { output.value = it; ApktoolSettings.savePathsAndWorkers(context, ApktoolSettings.maxWorkers(context), projects.value, it) }, label = { Text("Build output root") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp))
        Text("Standard für Decode: /apktool/projects/<App-Name>/; im Decode/Build-Dialog kann Same folder, Left panel, Right panel, App default oder Custom gewählt werden.", modifier = Modifier.padding(20.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SignatureSettingsPage(onBack: () -> Unit) {
    val context = LocalContext.current
    var value by remember { mutableStateOf(ApktoolSettings.signatureDefaults(context)) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val copied = runCatching { copySigningFileToPrivateStorage(context, uri) }.getOrNull()
            if (copied != null) {
                value = value.copy(profile = "custom", customKeystorePath = copied.absolutePath)
                ApktoolSettings.saveSignatureDefaults(context, value)
            }
        }
    }
    fun save(v: ApktoolSignatureDefaults) { value = v; ApktoolSettings.saveSignatureDefaults(context, v) }
    PageScaffold("Signatur", onBack) {
        Row(Modifier.fillMaxWidth().clickable { save(value.copy(profile = "testkey")) }.padding(20.dp), verticalAlignment = Alignment.CenterVertically) { RadioButton(value.profile == "testkey", onClick = { save(value.copy(profile = "testkey")) }); Text("Vorgabesignatur (testkey)") }
        Row(Modifier.fillMaxWidth().clickable { save(value.copy(profile = "custom")) }.padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) { RadioButton(value.profile == "custom", onClick = { save(value.copy(profile = "custom")) }); Text("Benutzerdefinierte Signatur") }
        if (value.profile == "custom") {
            ValueRow("Keystore auswählen", value.customKeystorePath.ifBlank { "Keine Datei" }) { picker.launch(arrayOf("application/octet-stream", "application/x-pkcs12", "*/*")) }
            OutlinedTextField(
                value = value.customKeystorePassword,
                onValueChange = { save(value.copy(customKeystorePassword = it)) },
                label = { Text("Keystore-Passwort") },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
            )
        }
        SwitchRow("Signatur v1", value = value.v1) { save(value.copy(v1 = it)) }
        SwitchRow("Signatur v2", value = value.v2) { save(value.copy(v2 = it)) }
        SwitchRow("Signatur v3", value = value.v3) { save(value.copy(v3 = it)) }
        SwitchRow("Signatur v4", value = value.v4) { save(value.copy(v4 = it)) }
    }
}

@Composable
private fun ArchiveSettingsPage(onBack: () -> Unit) {
    val context = LocalContext.current
    var value by remember { mutableStateOf(ArchiveSettings.load(context)) }
    var formatOpen by remember { mutableStateOf(false) }
    var levelOpen by remember { mutableStateOf(false) }
    fun save() = ArchiveSettings.save(context, value)
    PageScaffold("Archivierung", onBack) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
            TextButton(onClick = { formatOpen = true }, modifier = Modifier.weight(1f)) { Text("Format: ${value.format.label}") }
            DropdownMenu(formatOpen, { formatOpen = false }) { ArchiveFormat.entries.filter { it != ArchiveFormat.RAR }.forEach { f -> DropdownMenuItem(text = { Text(f.label) }, onClick = { value = value.copy(format = f); save(); formatOpen = false }) } }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
            TextButton(onClick = { levelOpen = true }, modifier = Modifier.weight(1f)) { Text("Kompression: ${value.level.label}") }
            DropdownMenu(levelOpen, { levelOpen = false }) { ArchiveLevel.entries.forEach { l -> DropdownMenuItem(text = { Text(l.label) }, onClick = { value = value.copy(level = l); save(); levelOpen = false }) } }
        }
        SwitchRow("Einträge einzeln komprimieren", value = value.compressEachIndependently) { value = value.copy(compressEachIndependently = it); save() }
        SwitchRow("Quelldateien nach Erfolg löschen", value = value.deleteSourcesAfterCompression) { value = value.copy(deleteSourcesAfterCompression = it); save() }
        SwitchRow("In das andere Panel komprimieren", value = value.compressToOtherPane) { value = value.copy(compressToOtherPane = it); save() }
        Text("Lesen/Entpacken: ZIP/JAR/APK, 7z, TAR, tar.gz, tar.bz2, tar.xz und RAR. Archive werden read-only geöffnet; Änderungen erfolgen durch Kopieren/Entpacken nach außen.", modifier = Modifier.padding(20.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun FrameworkSettingsPage(onBack: () -> Unit) {
    val context = LocalContext.current
    var tag by remember { mutableStateOf(ApktoolSettings.frameworkTag(context)) }
    PageScaffold("Frameworks", onBack) {
        Text("Framework-Verzeichnis", modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp), fontWeight = FontWeight.Bold)
        Text(ApktoolSettings.frameworkDir(), modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        ApktoolSettings.availableFrameworkTags(context).forEach { option -> Row(Modifier.fillMaxWidth().clickable { tag = option; ApktoolSettings.setFrameworkTag(context, option) }.padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) { RadioButton(tag == option, onClick = { tag = option; ApktoolSettings.setFrameworkTag(context, option) }); Text(ApktoolSettings.frameworkLabel(option)) } }
    }
}

@Composable
private fun Aapt2SettingsPage(onBack: () -> Unit) {
    val context = LocalContext.current
    var selected by remember { mutableStateOf(ApktoolSettings.aaptVariant(context)) }
    var custom by remember { mutableStateOf(ApktoolSettings.customAapt2Path(context)) }
    PageScaffold("AAPT2", onBack) {
        ApktoolSettings.aaptOptions.forEach { option -> Row(Modifier.fillMaxWidth().clickable { selected = option; ApktoolSettings.setAapt2(context, option, custom) }.padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) { RadioButton(selected == option, onClick = { selected = option; ApktoolSettings.setAapt2(context, option, custom) }); Text(ApktoolSettings.aaptLabel(option)) } }
        if (selected == "custom") OutlinedTextField(custom, { custom = it; ApktoolSettings.setAapt2(context, selected, it) }, label = { Text("AAPT2 Pfad") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(20.dp))
    }
}

@Composable
private fun ApkModulesSettingsPage(onBack: () -> Unit) {
    val context = LocalContext.current
    var value by remember { mutableStateOf(ApkModulePreferences.load(context)) }
    var alignment by remember { mutableStateOf(value.zipAlignment.toString()) }
    var soAlignment by remember { mutableStateOf(value.sharedLibraryAlignment.toString()) }
    var compression by remember { mutableStateOf(value.compressionLevel.toString()) }

    fun save(transform: (io.github.lootdev78.mtapktool.apktool.ApkModuleOptions) -> io.github.lootdev78.mtapktool.apktool.ApkModuleOptions) {
        value = transform(value)
        ApkModulePreferences.save(context, value)
    }

    PageScaffold("APK-Module", onBack) {
        Text("zipalign", modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        OutlinedTextField(
            alignment,
            {
                alignment = it.filter(Char::isDigit)
                save { old -> old.copy(zipAlignment = alignment.toIntOrNull() ?: 4) }
            },
            label = { Text("Alignment") },
            supportingText = { Text("Zweierpotenz, Standard 4") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
        )
        OutlinedTextField(
            soAlignment,
            {
                soAlignment = it.filter(Char::isDigit)
                save { old -> old.copy(sharedLibraryAlignment = soAlignment.toIntOrNull() ?: 16384) }
            },
            label = { Text(".so Page Alignment") },
            supportingText = { Text("0 deaktiviert; Standard 16384") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
        )
        SwitchRow("Vorhandene Ausgabe überschreiben", value = value.zipForce) { checked -> save { it.copy(zipForce = checked) } }
        SwitchRow("Ergebnis nach zipalign verifizieren", value = value.zipVerify) { checked -> save { it.copy(zipVerify = checked) } }

        Text("AntiSplit-M / APKS · APKM · XAPK · APKX → APK", modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        SwitchRow("Geräte-Splits automatisch auswählen", value = value.autoSelectDeviceSplits) { checked -> save { it.copy(autoSelectDeviceSplits = checked) } }
        SwitchRow("Optionale Ressourcen und Assets übernehmen", value = value.includeOptionalSplits) { checked -> save { it.copy(includeOptionalSplits = checked) } }
        SwitchRow("Feature-Splits übernehmen", value = value.includeFeatureSplits) { checked -> save { it.copy(includeFeatureSplits = checked) } }
        SwitchRow("Extrahierte Splits zusätzlich behalten", value = value.keepExtractedSplits) { checked -> save { it.copy(keepExtractedSplits = checked) } }
        SwitchRow("Ungültige META-INF Signaturen entfernen", value = value.cleanMetaInf) { checked -> save { it.copy(cleanMetaInf = checked) } }
        SwitchRow("Force Merge bei abweichenden Versionscodes", value = value.antiSplitForceMerge) { checked -> save { it.copy(antiSplitForceMerge = checked) } }
        SwitchRow("Split-Metadaten aus Manifest entfernen", value = value.antiSplitStripMetadata) { checked -> save { it.copy(antiSplitStripMetadata = checked) } }
        OutlinedTextField(
            compression,
            {
                compression = it.filter(Char::isDigit).take(1)
                save { old -> old.copy(compressionLevel = (compression.toIntOrNull() ?: 6).coerceIn(0, 9)) }
            },
            label = { Text("ZIP-Kompression 0–9") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
        )
        Text(
            "Die Einstellungen werden beim Split-Installer, bei APKS/APKM/XAPK/APKX → APK und bei Zipalign verwendet. Im Container-Fenster können die erkannten Base-, ABI-, Sprach-, Dichte- und Feature-Splits vor Installation oder Konvertierung einzeln gewählt werden.",
            modifier = Modifier.padding(20.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ApkExtractorSettingsPage(onBack: () -> Unit) {
    val context = LocalContext.current
    var value by remember { mutableStateOf(ApkExtractorPreferences.load(context)) }
    var compression by remember { mutableStateOf(value.compressionLevel.toString()) }
    fun save(updated: ApkExtractorOptions) { value = updated; ApkExtractorPreferences.save(context, updated) }
    PageScaffold("APK Extractor", onBack) {
        OutlinedTextField(
            value = value.outputRoot,
            onValueChange = { save(value.copy(outputRoot = it)) },
            label = { Text("Standard-Ausgabeordner") },
            supportingText = { Text("Standard: /storage/emulated/0/apktool/apks/<app>.apk bzw. <app>.apks") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
        )
        SwitchRow("System-Apps anzeigen", value.includeSystemApps) { save(value.copy(includeSystemApps = it)) }

        Text("Sortierung", modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp), fontWeight = FontWeight.SemiBold)
        listOf(
            "name" to "Name",
            "last_update" to "Letzte Aktualisierung",
            "first_install" to "Erstinstallation",
        ).forEach { (mode, label) ->
            Row(
                Modifier.fillMaxWidth().clickable { save(value.copy(sortMode = mode)) }.padding(horizontal = 20.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(value.sortMode == mode, onClick = { save(value.copy(sortMode = mode)) })
                Text(label)
            }
        }

        Text("Anzeigeinformationen", modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp), fontWeight = FontWeight.SemiBold)
        SwitchRow("App-Icon anzeigen", value.showIcon) { save(value.copy(showIcon = it)) }
        SwitchRow("App-Name anzeigen", value.showAppName) { save(value.copy(showAppName = it)) }
        SwitchRow("Paketname anzeigen", value.showPackageName) { save(value.copy(showPackageName = it)) }
        SwitchRow("Versionsname anzeigen", value.showVersionName) { save(value.copy(showVersionName = it)) }
        SwitchRow("Versionscode anzeigen", value.showVersionCode) { save(value.copy(showVersionCode = it)) }
        SwitchRow("Erstinstallation anzeigen", value.showFirstInstall) { save(value.copy(showFirstInstall = it)) }
        SwitchRow("Letzte Aktualisierung anzeigen", value.showLastUpdate) { save(value.copy(showLastUpdate = it)) }

        Text("Zusatzfunktionen im App-Dialog", modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp), fontWeight = FontWeight.SemiBold)
        SwitchRow("App-Icon extrahieren", value.showExtractIcon) { save(value.copy(showExtractIcon = it)) }
        SwitchRow("Ressourcen extrahieren", value.showExtractResources) { save(value.copy(showExtractResources = it)) }
        SwitchRow("DEX extrahieren", value.showExtractDex) { save(value.copy(showExtractDex = it)) }
        SwitchRow("AndroidManifest.xml extrahieren", value.showExtractManifest) { save(value.copy(showExtractManifest = it)) }
        SwitchRow("Base APK extrahieren", value.showExtractBase) { save(value.copy(showExtractBase = it)) }
        SwitchRow("Einzelnen Split extrahieren", value.showExtractSplit) { save(value.copy(showExtractSplit = it)) }
        SwitchRow("Native Libraries extrahieren", value.showExtractLibs) { save(value.copy(showExtractLibs = it)) }

        Text("Split-Apps standardmäßig", modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp), fontWeight = FontWeight.SemiBold)
        Row(Modifier.fillMaxWidth().clickable { save(value.copy(defaultSplitMode = "apks")) }.padding(horizontal = 20.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            RadioButton(value.defaultSplitMode == "apks", onClick = { save(value.copy(defaultSplitMode = "apks")) }); Text("als .apks speichern")
        }
        Row(Modifier.fillMaxWidth().clickable { save(value.copy(defaultSplitMode = "merge")) }.padding(horizontal = 20.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            RadioButton(value.defaultSplitMode == "merge", onClick = { save(value.copy(defaultSplitMode = "merge")) }); Text("mit AntiSplit-M zu .apk zusammenführen")
        }
        OutlinedTextField(
            value = compression,
            onValueChange = {
                compression = it.filter(Char::isDigit).take(1)
                save(value.copy(compressionLevel = (compression.toIntOrNull() ?: 6).coerceIn(0, 9)))
            },
            label = { Text("Kompression 0–9") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun ApkClonerSettingsPage(onBack: () -> Unit) {
    val context = LocalContext.current
    var value by remember { mutableStateOf(ApkClonerPreferences.load(context)) }
    fun save(updated: ApkClonerOptions) { value = updated; ApkClonerPreferences.save(context, updated) }
    PageScaffold("APK Cloner", onBack) {
        OutlinedTextField(
            value = value.suffix,
            onValueChange = { save(value.copy(suffix = it.ifBlank { "_clone" })) },
            label = { Text("Dateinamensuffix") },
            supportingText = { Text("Beispiel: app${value.suffix}.apk") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
        )
        SwitchRow("Klon standardmäßig im Quellordner speichern", value.outputSameFolder) { save(value.copy(outputSameFolder = it)) }
        Text(
            "APK Cloner ändert Paketname, Manifest-Permissions/Provider und resources.arsc. Der erzeugte Klon ist nach der Änderung nicht mehr original signiert und kann anschließend über die MTApktool-Signaturfunktion signiert werden.",
            modifier = Modifier.padding(20.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TextEditorSettingsPage(onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("editor_pref", Context.MODE_PRIVATE) }
    var wordWrap by remember { mutableStateOf(prefs.getBoolean("word_wrap", false)) }
    var autoComplete by remember { mutableStateOf(prefs.getBoolean("auto_complete", true)) }
    var lineNumbers by remember { mutableStateOf(prefs.getBoolean("show_line_numbers", true)) }
    var stickyLineNumbers by remember { mutableStateOf(prefs.getBoolean("sticky_line_numbers", true)) }
    var indentGuides by remember { mutableStateOf(prefs.getBoolean("show_indent_guides", true)) }
    var wrapArrows by remember { mutableStateOf(prefs.getBoolean("show_wrap_arrows", true)) }
    var autoIndent by remember { mutableStateOf(prefs.getBoolean("auto_indent", true)) }
    var menuStyle by remember { mutableStateOf(prefs.getInt("menu_style", 0).coerceIn(0, 2)) }
    var syntaxPosition by remember { mutableStateOf(prefs.getInt("syntax_position", 0).coerceAtLeast(0)) }
    var syntaxMenuOpen by remember { mutableStateOf(false) }
    val syntaxChoices = remember(context) {
        runCatching {
            val text = context.assets.open("availableSyntax.json").bufferedReader().use { it.readText() }
            val array = JSONArray(text)
            buildList {
                add("Text")
                for (index in 0 until array.length()) add(array.getJSONObject(index).optString("Syntax", "Syntax ${index + 1}"))
            }
        }.getOrElse { listOf("Text") }
    }
    val safeSyntaxPosition = syntaxPosition.takeIf { it in syntaxChoices.indices } ?: 0
    fun bool(key: String, value: Boolean) { prefs.edit().putBoolean(key, value).apply() }
    PageScaffold("Text Editor", onBack) {
        SwitchRow("Zeilenumbruch", wordWrap) { wordWrap = it; bool("word_wrap", it) }
        SwitchRow("Autovervollständigung", autoComplete) { autoComplete = it; bool("auto_complete", it) }
        SwitchRow("Zeilennummern", lineNumbers) { lineNumbers = it; bool("show_line_numbers", it) }
        SwitchRow("Sticky Zeilennummern", stickyLineNumbers) { stickyLineNumbers = it; bool("sticky_line_numbers", it) }
        SwitchRow("Einrückungslinien", indentGuides) { indentGuides = it; bool("show_indent_guides", it) }
        SwitchRow("Wrap-Pfeile", wrapArrows) { wrapArrows = it; bool("show_wrap_arrows", it) }
        SwitchRow("Auto-Indent", autoIndent) { autoIndent = it; bool("auto_indent", it) }
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { syntaxMenuOpen = true }, modifier = Modifier.weight(1f)) { Text("Syntax: ${syntaxChoices.getOrElse(safeSyntaxPosition) { "Text" }}") }
            DropdownMenu(expanded = syntaxMenuOpen, onDismissRequest = { syntaxMenuOpen = false }) {
                syntaxChoices.forEachIndexed { index, label ->
                    DropdownMenuItem(
                        text = { Text(label) },
                        onClick = {
                            syntaxPosition = index
                            prefs.edit().putInt("syntax_position", index).apply()
                            syntaxMenuOpen = false
                        },
                    )
                }
            }
        }
        Text("Auswahlmenü", modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp), fontWeight = FontWeight.SemiBold)
        listOf("Icon + Text", "Nur Text", "Nur Icon").forEachIndexed { index, label ->
            Row(Modifier.fillMaxWidth().clickable { menuStyle = index; prefs.edit().putInt("menu_style", index).apply() }.padding(horizontal = 20.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                RadioButton(menuStyle == index, onClick = { menuStyle = index; prefs.edit().putInt("menu_style", index).apply() }); Text(label)
            }
        }
    }
}

@Composable
private fun ApktoolCliPage(onBack: () -> Unit) {
    val context = LocalContext.current
    var command by remember { mutableStateOf("apktool --help") }
    var queued by remember { mutableStateOf<String?>(null) }
    PageScaffold("Apktool CLI", onBack) {
        Text(
            "Vollständige Apktool-Kommandos werden als Hintergrund-Job im separaten :apktool-Prozess ausgeführt. Der Output bleibt im rechten Task-Drawer verfügbar.",
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = command,
            onValueChange = { command = it },
            label = { Text("Command") },
            minLines = 5,
            maxLines = 12,
            textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
        )
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = { command = "apktool --help" }) { Text("HELP") }
            TextButton(onClick = { command = "apktool --version" }) { Text("VERSION") }
            Spacer(Modifier.weight(1f))
            TextButton(
                enabled = command.isNotBlank(),
                onClick = { queued = ApktoolJobService.enqueue(context, "CLI", command.trim()) },
            ) { Text("START") }
        }
        queued?.let {
            Text(
                "Job gestartet: $it",
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SimpleTextPage(title: String, onBack: () -> Unit, text: String) {
    Scaffold(topBar = { TopAppBar(title = { Text(title) }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück") } }) }) { insets -> Text(text, modifier = Modifier.fillMaxSize().padding(insets).padding(20.dp)) }
}

private fun copySigningFileToPrivateStorage(context: Context, uri: Uri): File {
    val dir = File(context.filesDir, "signing").apply { if (!isDirectory) mkdirs() }
    val out = File(dir, "custom-keystore.bin")
    context.contentResolver.openInputStream(uri).use { input ->
        requireNotNull(input) { "Keystore konnte nicht geöffnet werden" }
        out.outputStream().buffered().use { output -> input.copyTo(output) }
    }
    return out
}
