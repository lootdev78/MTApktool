package io.github.lootdev78.mtapktool.apktool

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.InstallMobile
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.lootdev78.mtapktool.feature.explorer.util.ApkArchiveInfo
import io.github.lootdev78.mtapktool.feature.explorer.util.ApkArchiveReader
import io.github.lootdev78.mtapktool.feature.explorer.util.sdkLabel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

@Composable
fun ApkInfoDialog(
    file: File,
    onDismiss: () -> Unit,
    onFunctions: () -> Unit,
    onView: () -> Unit,
    onInstall: () -> Unit,
) {
    val context = LocalContext.current
    val info by produceState<ApkArchiveInfo?>(initialValue = null, file.absolutePath, file.lastModified()) {
        value = withContext(Dispatchers.IO) { ApkArchiveReader.read(context, file) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val icon = info?.icon
                if (icon != null) {
                    Image(icon.asImageBitmap(), contentDescription = null, modifier = Modifier.size(58.dp))
                } else {
                    Icon(Icons.Default.Android, contentDescription = null, modifier = Modifier.size(58.dp))
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(info?.label ?: file.nameWithoutExtension, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(info?.versionName ?: "…", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        text = {
            if (info == null) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) { CircularProgressIndicator() }
            } else {
                Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState())) {
                    InfoRow("Paketname", info!!.packageName)
                    InfoRow("Versionscode", info!!.versionCode.toString())
                    InfoRow("Dateigröße", formatApkSize(file.length()))
                    InfoRow("Signatur", info!!.signatureSchemes)
                    InfoRow("Schutz", "Nicht geprüft")
                    InfoRow("Target SDK", sdkLabel(info!!.targetSdk))
                    InfoRow("Minimum SDK", sdkLabel(info!!.minSdk))
                    InfoRow("Installiert", info!!.installedVersion ?: "Nicht installiert")
                    info!!.installedDataDir?.let { InfoRow("Datenordner 1", it) }
                    info!!.externalDataDir?.let { InfoRow("Datenordner 2", it) }
                    info!!.installedApkPath?.let { InfoRow("APK-Pfad", it) }
                    info!!.installedUid?.let { InfoRow("UID", it.toString()) }
                }
            }
        },
        dismissButton = { TextButton(onClick = onFunctions) { Text("FUNKTIONEN") } },
        confirmButton = {
            Row {
                TextButton(onClick = onView) { Text("ANZEIGEN") }
                TextButton(onClick = onInstall) { Text("INSTALLIEREN") }
            }
        },
    )
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.Top) {
        Text(label, modifier = Modifier.width(125.dp), style = MaterialTheme.typography.bodyMedium)
        Text(value, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun ApkFunctionsDialog(
    file: File,
    onDismiss: () -> Unit,
    onDecode: () -> Unit,
    onImportFramework: () -> Unit,
    onFileInfo: () -> Unit,
    onOpenWith: () -> Unit,
    onShare: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Funktionen") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                FunctionCard(Icons.Default.Extension, "Dekompilieren", "APK mit Apktool in ein Projekt dekompilieren", onDecode)
                FunctionCard(Icons.Default.FolderOpen, "Als Framework importieren", "Über den vorhandenen Framework Manager installieren", onImportFramework)
                FunctionCard(Icons.Default.Description, "Dateiinformationen", file.name, onFileInfo)
                FunctionCard(Icons.Default.InstallMobile, "Öffnen mit", "APK an eine andere App übergeben", onOpenWith)
                FunctionCard(Icons.Default.Share, "Teilen", "APK über Android teilen", onShare)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("SCHLIESSEN") } },
    )
}

@Composable
private fun FunctionCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

private fun formatApkSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val mb = bytes / (1024.0 * 1024.0)
    return String.format(Locale.US, if (mb >= 100) "%.0f MB" else "%.1f MB", mb)
}
