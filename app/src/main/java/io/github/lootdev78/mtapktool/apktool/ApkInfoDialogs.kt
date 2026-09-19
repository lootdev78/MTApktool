package io.github.lootdev78.mtapktool.apktool

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.InstallMobile
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.lootdev78.mtapktool.feature.explorer.util.ApkArchiveInfo
import io.github.lootdev78.mtapktool.feature.explorer.util.ApkArchiveReader
import io.github.lootdev78.mtapktool.feature.explorer.util.ApkSignatureInfo
import io.github.lootdev78.mtapktool.feature.explorer.util.sdkLabel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Compact, square MT-style APK information dialog. */
@Composable
fun ApkInfoDialog(
    file: File,
    panelLabel: String? = null,
    onDismiss: () -> Unit,
    onFunctions: () -> Unit,
    onView: () -> Unit,
    onInstall: () -> Unit,
) {
    val context = LocalContext.current
    var showSignatureInfo by remember { mutableStateOf(false) }
    val info by produceState<ApkArchiveInfo?>(initialValue = null, file.absolutePath, file.lastModified()) {
        value = withContext(Dispatchers.IO) { ApkArchiveReader.read(context, file) }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.9f),
            shape = RoundedCornerShape(2.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurface,
            shadowElevation = 10.dp,
        ) {
            Column(Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val icon = info?.icon
                    if (icon != null) Image(icon.asImageBitmap(), null, Modifier.size(68.dp))
                    else Icon(Icons.Default.Android, null, Modifier.size(68.dp))
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(info?.label ?: file.nameWithoutExtension, fontSize = 23.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(info?.versionName ?: "…", fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                Spacer(Modifier.size(12.dp))
                if (info == null) {
                    Row(Modifier.fillMaxWidth().padding(vertical = 28.dp), verticalAlignment = Alignment.CenterVertically) {
                        Spacer(Modifier.weight(1f)); CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 2.dp); Spacer(Modifier.weight(1f))
                    }
                } else {
                    Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState())) {
                        InfoRow("Paketname", info!!.packageName)
                        InfoRow("Versionscode", info!!.versionCode.toString())
                        InfoRow("Dateigröße", formatApkSize(file.length()))
                        panelLabel?.let { InfoRow("Panel", it) }
                        InfoRow("Signatur", info!!.signatureSchemes, onClick = { showSignatureInfo = true })
                        InfoRow("Schutz", "Nicht erkannt")
                        InfoRow("Target SDK", sdkLabel(info!!.targetSdk))
                        InfoRow("Minimum SDK", sdkLabel(info!!.minSdk))
                        InfoRow("Installiert", info!!.installedVersion ?: "Nicht installiert")
                        info!!.installedDataDir?.let { InfoRow("Datenordner 1", it) }
                        info!!.externalDataDir?.let { InfoRow("Datenordner 2", it) }
                        info!!.installedApkPath?.let { InfoRow("APK-Pfad", it) }
                        info!!.firstInstallTime?.takeIf { it > 0 }?.let { InfoRow("Erste Installation", formatDate(it)) }
                        info!!.lastUpdateTime?.takeIf { it > 0 }?.let { InfoRow("Letztes Update", formatDate(it)) }
                        info!!.installedUid?.let { InfoRow("UID", it.toString()) }
                    }
                }

                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onFunctions) { Text("FUNKTIONEN") }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onView) { Text("ANZEIGEN") }
                    TextButton(onClick = onInstall) { Text("INSTALLIEREN") }
                }
            }
        }
    }

    if (showSignatureInfo) {
        SignatureInformationDialog(file = file, onDismiss = { showSignatureInfo = false })
    }
}

@Composable
private fun InfoRow(label: String, value: String, onClick: (() -> Unit)? = null) {
    val modifier = if (onClick != null) Modifier.fillMaxWidth().clickable(onClick = onClick) else Modifier.fillMaxWidth()
    Row(modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.Top) {
        Text(label, modifier = Modifier.width(130.dp), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Text(value, modifier = Modifier.weight(1f), fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun SignatureInformationDialog(file: File, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var addColons by remember { mutableStateOf(true) }
    var upperCase by remember { mutableStateOf(true) }
    var showRaw by remember { mutableStateOf(false) }
    var compareText by remember { mutableStateOf<String?>(null) }
    val signature by produceState<ApkSignatureInfo?>(null, file.absolutePath, file.lastModified(), addColons, upperCase) {
        value = withContext(Dispatchers.IO) { ApkArchiveReader.signatureInfo(file, addColons, upperCase) }
    }
    val compareLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                val result = withContext(Dispatchers.IO) {
                    runCatching {
                        val tmp = File(context.cacheDir, "signature-compare-${System.nanoTime()}.apk")
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            FileOutputStream(tmp).use { output -> input.copyTo(output, 1024 * 512) }
                        } ?: error("Datei kann nicht geöffnet werden")
                        val other = ApkArchiveReader.signatureInfo(tmp, addColons, upperCase)
                        tmp.delete()
                        if (other == null || signature == null) "Signatur nicht lesbar"
                        else if (other.sha256 == signature!!.sha256) "Signaturen sind identisch" else "Signaturen unterscheiden sich"
                    }.getOrElse { "Vergleich fehlgeschlagen: ${it.message}" }
                }
                compareText = result
            }
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
                Text("Signature information", fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.size(10.dp))
                val sig = signature
                if (sig == null) {
                    CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 2.dp)
                } else {
                    Column(Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState())) {
                        InfoRow("File", file.name)
                        InfoRow("Scheme", sig.schemes)
                        InfoRow("Status", sig.status)
                        InfoRow("Algorithm", sig.algorithm)
                        InfoRow("Public key", sig.publicKey)
                        InfoRow("Valid from", formatDate(sig.validFrom))
                        InfoRow("Valid until", formatDate(sig.validUntil))
                        InfoRow("Owner", sig.owner)
                        InfoRow("HASH", sig.hash)
                        InfoRow("CRC32", sig.crc32)
                        InfoRow("MD5", sig.md5)
                        InfoRow("SHA1", sig.sha1)
                        InfoRow("SHA256", sig.sha256)
                        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("Format", modifier = Modifier.width(130.dp), fontSize = 15.sp)
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) { Text("Add colon", Modifier.weight(1f)); Switch(addColons, { addColons = it }) }
                                Row(verticalAlignment = Alignment.CenterVertically) { Text("Upper case", Modifier.weight(1f)); Switch(upperCase, { upperCase = it }) }
                            }
                        }
                        compareText?.let { Text(it, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp)) }
                    }
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(enabled = signature != null, onClick = { showRaw = true }) { Text("VIEW DATA") }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { compareLauncher.launch(arrayOf("application/vnd.android.package-archive", "application/octet-stream")) }) { Text("COMPARE") }
                    TextButton(onClick = onDismiss) { Text("CLOSE") }
                }
            }
        }
    }

    if (showRaw) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showRaw = false },
            title = { Text("Certificate data") },
            text = {
                Text(
                    signature?.rawCertificateHex.orEmpty(),
                    modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                    fontSize = 11.sp,
                )
            },
            confirmButton = { TextButton(onClick = { showRaw = false }) { Text("CLOSE") } },
        )
    }
}

@Composable
fun ApkFunctionsDialog(
    file: File,
    onDismiss: () -> Unit,
    onDecode: () -> Unit,
    onImportFramework: () -> Unit,
    onClone: () -> Unit,
    onFileInfo: () -> Unit,
    onOpenWith: () -> Unit,
    onShare: () -> Unit,
) {
    val actions = listOf(
        Triple(Icons.Default.Extension, "Dekompilieren", onDecode),
        Triple(Icons.Default.FolderOpen, "Als Framework importieren", onImportFramework),
        Triple(Icons.Default.ContentCopy, "APK klonen", onClone),
        Triple(Icons.Default.Description, "Dateiinformationen", onFileInfo),
        Triple(Icons.Default.InstallMobile, "Öffnen mit…", onOpenWith),
        Triple(Icons.Default.Share, "Teilen", onShare),
    )
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.86f),
            shape = RoundedCornerShape(2.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            shadowElevation = 10.dp,
        ) {
            Column(Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 8.dp)) {
                Text("Funktionen", fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                Text(file.name, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.size(8.dp))
                actions.forEach { (icon, title, action) ->
                    FunctionRow(icon, title, action)
                }
                Row(Modifier.fillMaxWidth()) { Spacer(Modifier.weight(1f)); TextButton(onClick = onDismiss) { Text("SCHLIESSEN") } }
            }
        }
    }
}

@Composable
private fun FunctionRow(icon: ImageVector, title: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, Modifier.size(25.dp))
        Spacer(Modifier.width(16.dp))
        Text(title, fontSize = 17.sp)
    }
}

private fun formatDate(time: Long): String =
    SimpleDateFormat("dd-MM-yyyy HH:mm:ss", Locale.getDefault()).format(Date(time))

private fun formatApkSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val mb = bytes / (1024.0 * 1024.0)
    return String.format(Locale.US, if (mb >= 100) "%.0f MB" else "%.2f MB", mb)
}
