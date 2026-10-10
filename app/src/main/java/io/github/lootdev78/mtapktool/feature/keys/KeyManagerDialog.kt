package io.github.lootdev78.mtapktool.feature.keys

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import io.github.lootdev78.mtcrypto.KeyStoreTools
import io.github.lootdev78.mtapktool.apktool.ApktoolSettings
import io.github.lootdev78.mtapktool.apktool.ApktoolSignatureDefaults
import io.github.lootdev78.mtapktool.core.theme.MtClassicAlertDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import io.github.lootdev78.mtapktool.tasks.ToolTaskRegistry
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.security.cert.X509Certificate
import java.util.UUID

@Composable
fun KeyManagerDialog(onBack: () -> Unit, initialPath: String? = null) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val current = remember { ApktoolSettings.signatureDefaults(context) }
    var profile by remember { mutableStateOf(if (initialPath != null) "custom" else current.profile) }
    var path by remember { mutableStateOf(initialPath ?: current.customKeystorePath) }
    var storePassword by remember { mutableStateOf(if (initialPath != null) "" else current.customKeystorePassword) }
    var keyPassword by remember { mutableStateOf(if (initialPath != null) "" else current.customKeyPassword) }
    var alias by remember { mutableStateOf(if (initialPath != null) "" else current.customKeystoreAlias) }
    var aliases by remember { mutableStateOf<List<String>>(emptyList()) }
    var certificate by remember { mutableStateOf("") }
    var certificatePem by remember { mutableStateOf<ByteArray?>(null) }
    var pk8 by remember { mutableStateOf<ByteArray?>(null) }
    var files by remember { mutableStateOf<List<File>>(emptyList()) }
    var v1 by remember { mutableStateOf(current.v1) }; var v2 by remember { mutableStateOf(current.v2) }
    var v3 by remember { mutableStateOf(current.v3) }; var v4 by remember { mutableStateOf(current.v4) }
    var busy by remember { mutableStateOf(false) }; var error by remember { mutableStateOf<String?>(null) }
    var showGenerate by remember { mutableStateOf(false) }
    var exportFile by remember { mutableStateOf<File?>(null) }; var exportBytes by remember { mutableStateOf<ByteArray?>(null) }
    val keysDirectory = remember { File(context.filesDir, "signing-keys").apply { mkdirs() } }
    LaunchedEffect(path) { files = withContext(Dispatchers.IO) { keysDirectory.listFiles()?.filter { it.extension.lowercase() in setOf("jks", "keystore", "p12", "pfx", "bks") }?.sortedBy { it.name }.orEmpty() } }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri != null) scope.launch {
            busy = true
            error = withContext(Dispatchers.IO) { runCatching {
                context.contentResolver.openOutputStream(uri, "wt")?.use { out ->
                    val file = exportFile; val bytes = exportBytes
                    if (file != null) file.inputStream().use { it.copyTo(out) } else if (bytes != null) out.write(bytes) else error("Export fehlt")
                } ?: error("Exportziel nicht beschreibbar")
            }.exceptionOrNull()?.message }
            busy = false; exportBytes = null; exportFile = null
        }
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            busy = true; error = null
            val result = withContext(Dispatchers.IO) { runCatching {
                val file = File(keysDirectory, "import-${UUID.randomUUID()}.keystore")
                try {
                    context.contentResolver.openInputStream(uri)?.use { input -> file.outputStream().use { out ->
                        val buffer = ByteArray(65536); var total = 0L
                        while (true) { val n = input.read(buffer); if (n < 0) break; total += n; if (total > 16 * 1024 * 1024) throw IOException("Keystore größer als 16 MiB"); if (n > 0) out.write(buffer, 0, n) }
                    } } ?: throw IOException("Datei nicht lesbar")
                    file.absolutePath
                } catch (e: Throwable) { file.delete(); throw e }
            } }
            result.onSuccess { path = it; profile = "custom"; alias = ""; aliases = emptyList(); certificate = ""; certificatePem = null; pk8 = null; storePassword = ""; keyPassword = "" }
                .onFailure { error = it.message }
            busy = false
        }
    }
    fun inspect(save: Boolean) {
        busy = true; error = null
        scope.launch {
            val result = withContext(Dispatchers.IO) { runCatching {
                val store = KeyStoreTools.open(File(path), storePassword.toCharArray())
                val names = KeyStoreTools.keyAliases(store)
                if (names.isEmpty()) throw IOException("Kein privater Schlüssel enthalten")
                val chosen = alias.takeIf { it in names } ?: names.first()
                val entry = KeyStoreTools.keyEntry(store, chosen, keyPassword.ifBlank { storePassword }.toCharArray())
                val cert = entry.certificate as X509Certificate
                Triple(names, chosen, Triple(KeyStoreTools.certificateInfo(cert), KeyStoreTools.pem(cert).toByteArray(), entry.privateKey.encoded))
            } }
            result.onSuccess { (names, chosen, details) ->
                aliases = names; alias = chosen; certificate = details.first; certificatePem = details.second; pk8 = details.third
                if (save) {
                    runCatching { ApktoolSettings.saveSignatureDefaults(context, ApktoolSignatureDefaults(profile, path, storePassword, v1, v2, v3, v4, alias, keyPassword)) }
                        .onSuccess { onBack() }.onFailure { error = it.message }
                }
            }.onFailure { error = it.message ?: "Keystore konnte nicht gelesen werden" }
            busy = false
        }
    }
    MtClassicAlertDialog(onDismissRequest = { if (!busy) onBack() }, title = { Text("Schlüssel & Zertifikate") }, text = {
        Column(Modifier.heightIn(max = 500.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row {
                TextButton(enabled = !busy, onClick = { profile = "testkey" }) { Text((if (profile == "testkey") "✓ " else "") + "Testkey") }
                TextButton(enabled = !busy, onClick = { profile = "custom" }) { Text((if (profile == "custom") "✓ " else "") + "Eigener Schlüssel") }
            }
            if (profile == "custom") {
                files.forEach { file -> TextButton(enabled = !busy, onClick = { path = file.absolutePath; aliases = emptyList(); alias = ""; certificate = ""; certificatePem = null; pk8 = null }) { Text(file.name) } }
                OutlinedTextField(path, { path = it; aliases = emptyList(); certificatePem = null; pk8 = null }, label = { Text("Keystore (JKS / PKCS12 / BKS)") }, singleLine = true, enabled = !busy, modifier = Modifier.fillMaxWidth())
                Row {
                    TextButton(enabled = !busy, onClick = { importer.launch(arrayOf("*/*")) }) { Text("IMPORTIEREN") }
                    TextButton(enabled = !busy, onClick = { showGenerate = true }) { Text("NEUER SCHLÜSSEL") }
                }
                OutlinedTextField(storePassword, { storePassword = it; certificatePem = null; pk8 = null }, label = { Text("Keystore-Passwort") }, singleLine = true, enabled = !busy, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
                OutlinedTextField(keyPassword, { keyPassword = it; pk8 = null }, label = { Text("Schlüsselpasswort (leer = Keystore)") }, singleLine = true, enabled = !busy, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
                OutlinedTextField(alias, { alias = it; certificatePem = null; pk8 = null }, label = { Text("Alias") }, singleLine = true, enabled = !busy, modifier = Modifier.fillMaxWidth())
                aliases.forEach { name -> TextButton(enabled = !busy, onClick = { alias = name; certificatePem = null; pk8 = null; certificate = "" }) { Text((if (alias == name) "✓ " else "") + name) } }
                TextButton(enabled = !busy && path.isNotBlank(), onClick = { inspect(false) }) { Text("PRÜFEN / ZERTIFIKAT ANZEIGEN") }
                if (certificate.isNotBlank()) Text(certificate, style = MaterialTheme.typography.bodySmall)
                Row {
                    TextButton(enabled = !busy && path.isNotBlank(), onClick = { exportFile = File(path); exportBytes = null; exporter.launch(File(path).name) }) { Text("KEYSTORE EXPORT") }
                    TextButton(enabled = !busy && certificatePem != null, onClick = { exportFile = null; exportBytes = certificatePem; exporter.launch("$alias.x509.pem") }) { Text("PEM EXPORT") }
                }
                TextButton(enabled = !busy && pk8 != null, onClick = { exportFile = null; exportBytes = pk8; exporter.launch("$alias.pk8") }) { Text("PRIVATEN PK8-SCHLÜSSEL EXPORTIEREN") }
                Text("Passwörter werden mit Android Keystore verschlüsselt. Der gewählte Alias wird beim APK-Build, Signieren und bei Split-APKs verwendet.", style = MaterialTheme.typography.bodySmall)
            }
            listOf("Signatur v1", "Signatur v2", "Signatur v3", "Signatur v4").forEachIndexed { index, label ->
                Row { Checkbox(listOf(v1, v2, v3, v4)[index], { value -> when(index) { 0 -> v1 = value; 1 -> v2 = value; 2 -> v3 = value; 3 -> v4 = value } }, enabled = !busy); Text(label, Modifier.padding(top = 12.dp)) }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        }
    }, dismissButton = { TextButton(enabled = !busy, onClick = onBack) { Text("ABBRECHEN") } }, confirmButton = {
        TextButton(enabled = !busy && (v1 || v2 || v3 || v4), onClick = {
            if (profile == "custom") inspect(true)
            else runCatching { ApktoolSettings.saveSignatureDefaults(context, ApktoolSignatureDefaults(profile = "testkey", v1 = v1, v2 = v2, v3 = v3, v4 = v4)); onBack() }.onFailure { error = it.message }
        }) { Text("SPEICHERN") }
    })
    if (showGenerate) KeyGenerateDialog(keysDirectory, { showGenerate = false }) { file, generatedAlias, password, privatePassword ->
        path = file.absolutePath; profile = "custom"; alias = generatedAlias; storePassword = password; keyPassword = privatePassword
        aliases = emptyList(); certificate = ""; certificatePem = null; pk8 = null; showGenerate = false
        inspect(false)
    }
}

@Composable
private fun KeyGenerateDialog(directory: File, onDismiss: () -> Unit, onGenerated: (File, String, String, String) -> Unit) {
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("release") }; var alias by remember { mutableStateOf("release") }
    var password by remember { mutableStateOf("") }; var keyPassword by remember { mutableStateOf("") }
    var format by remember { mutableStateOf("JKS") }; var algorithm by remember { mutableStateOf("RSA") }
    var bits by remember { mutableIntStateOf(2048) }; var days by remember { mutableStateOf("10000") }
    var cn by remember { mutableStateOf("") }; var organization by remember { mutableStateOf("") }
    var unit by remember { mutableStateOf("") }; var city by remember { mutableStateOf("") }
    var state by remember { mutableStateOf("") }; var country by remember { mutableStateOf("") }
    var pair by remember { mutableStateOf(false) }; var busy by remember { mutableStateOf(false) }; var error by remember { mutableStateOf<String?>(null) }
    MtClassicAlertDialog(onDismissRequest = { if (!busy) onDismiss() }, title = { Text("Signaturschlüssel erzeugen") }, text = {
        Column(Modifier.heightIn(max = 470.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedTextField(name, { name = it }, label = { Text("Dateiname") }, singleLine = true, enabled = !busy)
            OutlinedTextField(alias, { alias = it }, label = { Text("Alias") }, singleLine = true, enabled = !busy)
            OutlinedTextField(password, { password = it }, label = { Text("Keystore-Passwort (mind. 6 Zeichen)") }, singleLine = true, enabled = !busy, visualTransformation = PasswordVisualTransformation())
            OutlinedTextField(keyPassword, { keyPassword = it }, label = { Text("Schlüsselpasswort (leer = Keystore)") }, singleLine = true, enabled = !busy, visualTransformation = PasswordVisualTransformation())
            Row { listOf("JKS", "PKCS12").forEach { value -> TextButton(enabled = !busy, onClick = { format = value }) { Text((if (format == value) "✓ " else "") + value) } } }
            Row { listOf("RSA", "EC").forEach { value -> TextButton(enabled = !busy, onClick = { algorithm = value; bits = if (value == "RSA") 2048 else 256 }) { Text((if (algorithm == value) "✓ " else "") + value) } } }
            Row { (if (algorithm == "RSA") listOf(2048, 3072, 4096) else listOf(256, 384)).forEach { value -> TextButton(enabled = !busy, onClick = { bits = value }) { Text((if (bits == value) "✓ " else "") + value) } } }
            OutlinedTextField(days, { days = it.filter(Char::isDigit) }, label = { Text("Gültigkeit in Tagen (1–36500)") }, singleLine = true, enabled = !busy)
            OutlinedTextField(cn, { cn = it }, label = { Text("Name / Common Name") }, singleLine = true, enabled = !busy)
            OutlinedTextField(organization, { organization = it }, label = { Text("Organisation (O)") }, singleLine = true, enabled = !busy)
            OutlinedTextField(unit, { unit = it }, label = { Text("Organisationseinheit (OU)") }, singleLine = true, enabled = !busy)
            OutlinedTextField(city, { city = it }, label = { Text("Ort (L)") }, singleLine = true, enabled = !busy)
            OutlinedTextField(state, { state = it }, label = { Text("Bundesland (ST)") }, singleLine = true, enabled = !busy)
            OutlinedTextField(country, { country = it.uppercase().take(2) }, label = { Text("Land (C, z. B. DE)") }, singleLine = true, enabled = !busy)
            Row { Checkbox(pair, { pair = it }, enabled = !busy); Text("Zusätzlich PK8 + X.509 PEM erzeugen", Modifier.padding(top = 12.dp)) }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }; if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        }
    }, dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("ABBRECHEN") } }, confirmButton = {
        TextButton(enabled = !busy, onClick = {
            busy = true; error = null
            scope.launch {
                val result = runCatching { ToolTaskRegistry.run("Keystore erzeugen", name) { taskId -> withContext(Dispatchers.IO) {
                    require(name.matches(Regex("[A-Za-z0-9._-]{1,80}")) && name != "." && name != "..") { "Ungültiger Dateiname" }
                    require(cn.isNotBlank()) { "Name / Common Name fehlt" }
                    require(country.isBlank() || country.matches(Regex("[A-Z]{2}"))) { "Land muss aus zwei Buchstaben bestehen" }
                    val distinguishedName = listOf("CN" to cn, "OU" to unit, "O" to organization, "L" to city, "ST" to state, "C" to country)
                        .filter { it.second.isNotBlank() }.joinToString(",") { "${it.first}=${KeyStoreTools.escapeDn(it.second)}" }
                    val file = File(directory, "$name.${if (format == "JKS") "jks" else "p12"}")
                    val privatePassword = keyPassword.ifBlank { password }
                    if (pair) require(!File(directory, "$name.pk8").exists() && !File(directory, "$name.x509.pem").exists()) { "PK8/PEM-Datei existiert bereits" }
                    KeyStoreTools.generate(file, format, alias, password.toCharArray(), privatePassword.toCharArray(), algorithm, bits, days.toIntOrNull() ?: 0, distinguishedName)
                    if (pair) {
                        val entry = KeyStoreTools.keyEntry(KeyStoreTools.open(file, password.toCharArray()), alias, privatePassword.toCharArray())
                        File(directory, "$name.pk8").outputStream().use { it.write(entry.privateKey.encoded) }
                        File(directory, "$name.x509.pem").writeText(KeyStoreTools.pem(entry.certificate as X509Certificate))
                    }
                    ToolTaskRegistry.finish(taskId, outputPath = file.absolutePath)
                    file
                } } }
                result.onSuccess { onGenerated(it, alias, password, keyPassword.ifBlank { password }) }.onFailure { error = it.message }
                busy = false
            }
        }) { Text("GENERIEREN") }
    })
}
