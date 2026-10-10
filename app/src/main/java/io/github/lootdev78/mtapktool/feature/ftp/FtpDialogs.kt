package io.github.lootdev78.mtapktool.feature.ftp

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import io.github.lootdev78.mtftp.MtFtpClient
import io.github.lootdev78.mtapktool.core.theme.MtClassicAlertDialog
import io.github.lootdev78.mtapktool.feature.explorer.viewmodel.ActivePane
import java.io.File
import java.util.UUID

@Composable
fun FtpClientDialog(initialPane: ActivePane, onDismiss: () -> Unit, onConnect: (ActivePane, FtpProfile, String, (String?) -> Unit) -> Unit) {
    val context = LocalContext.current
    var pane by remember { mutableStateOf(initialPane) }
    var profiles by remember { mutableStateOf(FtpProfileStore.load(context)) }
    var name by remember { mutableStateOf("") }
    var host by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("21") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var directory by remember { mutableStateOf("/") }
    var security by remember { mutableStateOf(MtFtpClient.Security.FTP) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    MtClassicAlertDialog(onDismissRequest = { if (!busy) onDismiss() }, title = { Text("FTP-Client") }, text = {
        Column(Modifier.heightIn(max = 470.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (profiles.isNotEmpty()) {
                Text("Gespeicherte Verbindungen", style = MaterialTheme.typography.labelMedium)
                profiles.forEach { profile ->
                    Row {
                        TextButton(enabled = !busy, modifier = Modifier.weight(1f), onClick = {
                            name = profile.name; host = profile.host; port = profile.port.toString(); username = profile.username
                            directory = profile.directory; security = profile.security; password = ""
                        }) { Text(profile.name) }
                        TextButton(enabled = !busy, onClick = { FtpProfileStore.remove(context, profile.name); profiles = FtpProfileStore.load(context) }) { Text("Entfernen") }
                    }
                }
                HorizontalDivider()
            }
            OutlinedTextField(name, { name = it }, label = { Text("Profilname (optional)") }, enabled = !busy, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(host, { host = it }, label = { Text("Host / IP") }, enabled = !busy, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(port, { port = it.filter(Char::isDigit) }, label = { Text("Port") }, enabled = !busy, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(username, { username = it }, label = { Text("Benutzername") }, enabled = !busy, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(password, { password = it }, label = { Text("Passwort") }, enabled = !busy, singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
            OutlinedTextField(directory, { directory = it }, label = { Text("Startordner") }, enabled = !busy, singleLine = true, modifier = Modifier.fillMaxWidth())
            MtFtpClient.Security.values().forEach { option ->
                Row { RadioButton(selected = security == option, enabled = !busy, onClick = { security = option; port = if (option == MtFtpClient.Security.IMPLICIT_TLS) "990" else "21" })
                    TextButton(enabled = !busy, onClick = { security = option }) { Text(when(option) { MtFtpClient.Security.FTP -> "FTP"; MtFtpClient.Security.EXPLICIT_TLS -> "FTPS • explizites TLS"; else -> "FTPS • implizites TLS" }) } }
            }
            Row { ActivePane.entries.forEach { option -> TextButton(enabled = !busy, onClick = { pane = option }) { Text((if (pane == option) "✓ " else "") + if (option == ActivePane.LEFT) "Linkes Panel" else "Rechtes Panel") } } }
            Text("Profile speichern keine Passwörter. FTP ist unverschlüsselt; FTPS prüft das Serverzertifikat.", style = MaterialTheme.typography.bodySmall)
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        }
    }, dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("ABBRECHEN") } }, confirmButton = {
        TextButton(enabled = !busy && host.isNotBlank() && (port.toIntOrNull() ?: 0) in 1..65535, onClick = {
            busy = true; error = null
            val profile = FtpProfile(name.trim(), host.trim(), port.toInt(), username, directory, security)
            onConnect(pane, profile, password) { message ->
                busy = false; error = message
                if (message == null) { if (profile.name.isNotBlank()) FtpProfileStore.save(context, profile); password = ""; onDismiss() }
            }
        }) { Text("VERBINDEN") }
    })
}

@Composable
fun FtpServerDialog(initialFolder: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val state by FtpServerService.state.collectAsState()
    var folder by remember { mutableStateOf(initialFolder) }
    var port by remember { mutableStateOf("2121") }
    var user by remember { mutableStateOf("mtapktool") }
    var password by remember { mutableStateOf(UUID.randomUUID().toString().take(12)) }
    var writable by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val locked = state.running || state.starting
    MtClassicAlertDialog(onDismissRequest = onDismiss, title = { Text("FTP-Server") }, text = {
        Column(Modifier.heightIn(max = 450.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (state.running) {
                state.addresses.forEach { address ->
                    Text(address)
                    Row {
                        TextButton(onClick = { context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("FTP", address)) }) { Text("KOPIEREN") }
                        TextButton(onClick = { context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, address), "FTP-Adresse teilen")) }) { Text("TEILEN") }
                    }
                }
                if (state.addresses.isEmpty()) Text("Keine LAN-Adresse gefunden. WLAN / Hotspot prüfen.")
                Text("Freigabe: ${state.folder}")
            }
            OutlinedTextField(folder, { folder = it }, label = { Text("Freigegebener Ordner") }, enabled = !locked, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(port, { port = it.filter(Char::isDigit) }, label = { Text("Port (1024–65535)") }, enabled = !locked, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(user, { user = it }, label = { Text("Benutzername") }, enabled = !locked, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(password, { password = it }, label = { Text("Passwort") }, enabled = !locked, singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
            Row { Checkbox(writable, { writable = it }, enabled = !locked); Text("Schreibzugriff zulassen", Modifier.padding(top = 12.dp)) }
            Text("Freigabe nur im gewählten Ordner. Unverschlüsseltes FTP für ein vertrauenswürdiges lokales Netz. Stoppen ist auch über die Benachrichtigung möglich.", style = MaterialTheme.typography.bodySmall)
            (error ?: state.error)?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (state.starting) LinearProgressIndicator(Modifier.fillMaxWidth())
        }
    }, dismissButton = { TextButton(onClick = onDismiss) { Text("SCHLIESSEN") } }, confirmButton = {
        TextButton(enabled = !state.starting, onClick = {
            if (state.running) FtpServerService.stop(context) else {
                error = runCatching {
                    require(File(folder).isDirectory) { "Ordner existiert nicht oder ist nicht zugänglich" }
                    require((port.toIntOrNull() ?: 0) in 1024..65535) { "Ungültiger Port" }
                    require(user.isNotBlank() && password.isNotBlank()) { "Benutzername und Passwort erforderlich" }
                    FtpServerService.start(context, folder, port.toInt(), user, password, writable)
                }.exceptionOrNull()?.message
            }
        }) { Text(if (state.running) "STOPPEN" else "STARTEN") }
    })
}
