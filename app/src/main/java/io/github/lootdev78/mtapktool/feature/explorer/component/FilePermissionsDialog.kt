package io.github.lootdev78.mtapktool.feature.explorer.component

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.lootdev78.mtapktool.core.theme.MtClassicAlertDialog
import io.github.lootdev78.mtapktool.feature.explorer.model.FileItem
import io.github.lootdev78.mtapktool.feature.explorer.util.FilePermissions
import io.github.lootdev78.mtapktool.feature.explorer.util.UnixFilePermissions
import io.github.lootdev78.mtapktool.tasks.ToolTaskRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun FilePermissionsDialog(item: FileItem, onDismiss: () -> Unit, onChanged: () -> Unit, onDocumentGrant: () -> Unit,
                          readRemote: suspend () -> Int?, writeRemote: suspend (Int) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var snapshot by remember(item.path) { mutableStateOf<UnixFilePermissions?>(null) }
    var mode by remember(item.path) { mutableStateOf("0644") }
    var uid by remember(item.path) { mutableStateOf("") }
    var gid by remember(item.path) { mutableStateOf("") }
    var root by remember { mutableStateOf(false) }
    var recursive by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var access by remember { mutableStateOf("") }
    LaunchedEffect(item.path) {
        try {
            withContext(Dispatchers.IO) {
                if (item.isSaf) {
                    val document = androidx.documentfile.provider.DocumentFile.fromSingleUri(context, Uri.parse(item.path))
                    access = "Lesen: ${document?.canRead() == true}\nSchreiben: ${document?.canWrite() == true}\nAndroid-Dokumentanbieter vergeben Zugriffsrechte über die System-Dateiauswahl. Unix-Modi sind dort nicht verfügbar."
                } else if (item.isFtp) {
                    val actual = readRemote()
                    if (actual != null) mode = actual.toString(8).padStart(3, '0') else access = "Der Server meldet keinen Unix-Modus. Einen gewünschten Modus explizit eingeben."
                } else {
                    snapshot = FilePermissions.read(item.file)
                    snapshot?.let { mode = it.octal; uid = it.uid.toString(); gid = it.gid.toString() }
                }
            }
        } catch (failure: Exception) { error = failure.message }
        finally { busy = false }
    }
    MtClassicAlertDialog(onDismissRequest = { if (!busy) onDismiss() }, title = { Text("Dateiberechtigungen") }, text = {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(item.name)
            if (item.isSaf) {
                Text(access)
                TextButton(onClick = onDocumentGrant) { Text("ORDNERZUGRIFF ERLAUBEN") }
                TextButton(onClick = { context.startActivity(Intent(if (android.os.Build.VERSION.SDK_INT >= 30) Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION else Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))) }) { Text("ANDROID-SPEICHERRECHTE") }
            } else {
                OutlinedTextField(mode, { value -> if (value.length <= 4 && value.all { it in '0'..'7' }) mode = value }, label = { Text("Unix-Modus (oktal, z. B. 0755)") }, singleLine = true, enabled = !busy)
                val numeric = mode.toIntOrNull(8) ?: 0
                listOf("Besitzer", "Gruppe", "Andere").forEachIndexed { group, label ->
                    Row {
                        Text(label, Modifier.weight(1f).padding(top = 12.dp))
                        listOf("r", "w", "x").forEachIndexed { bit, name ->
                            val mask = 1 shl (8 - group * 3 - bit)
                            Checkbox(numeric and mask != 0, { checked -> mode = (if (checked) numeric or mask else numeric and mask.inv()).toString(8).padStart(4, '0') }, enabled = !busy)
                            Text(name, Modifier.padding(top = 12.dp))
                        }
                    }
                }
                if (!item.isFtp) {
                    Row {
                        OutlinedTextField(uid, { uid = it.filter(Char::isDigit) }, Modifier.weight(1f), label = { Text("UID") }, enabled = !busy)
                        Spacer(Modifier.width(8.dp))
                        OutlinedTextField(gid, { gid = it.filter(Char::isDigit) }, Modifier.weight(1f), label = { Text("GID") }, enabled = !busy)
                    }
                    Row { Checkbox(root, { root = it }, enabled = !busy); Text("Root verwenden (su)", Modifier.padding(top = 12.dp)) }
                    if (item.isDirectory) Row { Checkbox(recursive, { recursive = it }, enabled = !busy); Text("Auf Unterordner und Dateien anwenden", Modifier.padding(top = 12.dp)) }
                    Text("Shared Storage und SELinux können Änderungen begrenzen. MTAPKTool zeigt Fehler des Dateisystems an.", style = MaterialTheme.typography.bodySmall)
                } else { Text("FTP: SITE CHMOD muss vom Server unterstützt werden.", style = MaterialTheme.typography.bodySmall); if (access.isNotBlank()) Text(access) }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        }
    }, dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("SCHLIESSEN") } }, confirmButton = {
        if (!item.isSaf) TextButton(enabled = !busy && mode.toIntOrNull(8) != null && snapshot?.symlink != true, onClick = {
            busy = true; error = null
            scope.launch {
                try {
                    ToolTaskRegistry.run("Dateirechte ändern", item.path) {
                        if (item.isFtp) writeRemote(mode.toInt(8)) else withContext(Dispatchers.IO) {
                            FilePermissions.apply(item.file, mode.toInt(8), uid.toInt(), gid.toInt(), recursive, root) { this@launch.ensureActive() }
                        }
                    }
                    onChanged(); onDismiss()
                } catch (failure: Exception) { error = failure.message ?: "Rechte konnten nicht geändert werden" }
                finally { busy = false }
            }
        }) { Text("ANWENDEN") }
    })
}
