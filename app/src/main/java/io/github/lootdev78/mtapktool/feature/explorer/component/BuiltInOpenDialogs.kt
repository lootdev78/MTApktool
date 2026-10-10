package io.github.lootdev78.mtapktool.feature.explorer.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.InstallMobile
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material3.Switch
import io.github.lootdev78.mtapktool.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.net.Uri
import android.webkit.MimeTypeMap
import java.io.File
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.lootdev78.mtapktool.feature.explorer.model.FileItem
import io.github.lootdev78.mtapktool.feature.explorer.state.isArchiveFile
import io.github.lootdev78.mtapktool.feature.explorer.state.isEditableTextFile
import io.github.lootdev78.mtapktool.feature.explorer.state.isImageFile
import io.github.lootdev78.mtapktool.feature.explorer.state.isAudioFile
import io.github.lootdev78.mtapktool.feature.explorer.state.isVideoFile
import io.github.lootdev78.mtapktool.settings.ExplorerPreferences

private data class BuiltInAction(
    val id: String,
    val title: String,
    val subtitle: String? = null,
    val icon: ImageVector,
    val onClick: () -> Unit,
    val drawable: Int? = null,
)

/**
 * Three-column opening chooser adapted to the tools available in MTApktool.
 */
@Composable
fun BuiltInOpenDialog(
    item: FileItem,
    onDismiss: () -> Unit,
    onTextEditor: () -> Unit,
    onArchiveViewer: () -> Unit,
    onApkInfo: () -> Unit,
    onSplitFunctions: () -> Unit,
    onApktoolDecode: () -> Unit,
    onRevealInPanel: () -> Unit,
    onImageViewer: () -> Unit,
    onMediaPlayer: () -> Unit,
    onKeyImport: () -> Unit,
    onExternalApp: (String?) -> Unit,
) {
    val context = LocalContext.current
    ExplorerPreferences.init(context)
    val prefs by ExplorerPreferences.state.collectAsState()
    val ext = item.extensionName.lowercase()
    val mime = item.mimeType.orEmpty().lowercase()
    val split = ext in setOf("apks", "apkm", "xapk", "apkx")
    val apk = ext == "apk" || mime == "application/vnd.android.package-archive"
    val textLike = item.isEditableTextFile() || mime.startsWith("text/") || mime in setOf("application/json", "application/xml", "text/xml")
    val archiveMime = mime in setOf("application/zip", "application/x-7z-compressed", "application/x-tar", "application/gzip", "application/x-xz", "application/java-archive")
    val archive = item.isArchiveFile() || ext == "jar" || apk || split || archiveMime
    val mimePrefs = remember(context) { context.getSharedPreferences("explorer_open_with", 0) }
    var useActualMime by remember { mutableStateOf(mimePrefs.getBoolean("actual_mime", false)) }
    val reportedMime = item.mimeType ?: MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "application/octet-stream"
    var actualMime by remember(item.path) { mutableStateOf<String?>(null) }
    LaunchedEffect(item.path) {
        actualMime = withContext(Dispatchers.IO) {
            runCatching {
                val input = when {
                    item.isFtp -> null
                    item.isSaf -> context.contentResolver.openInputStream(Uri.parse(item.path))
                    else -> File(item.path).inputStream()
                }
                input?.use { stream ->
                    val bytes = ByteArray(64); val n = stream.read(bytes)
                    val prefix = bytes.take(n.coerceAtLeast(0)).toByteArray()
                    when {
                        prefix.size >= 4 && prefix[0] == 0x50.toByte() && prefix[1] == 0x4b.toByte() -> if (apk) "application/vnd.android.package-archive" else "application/zip"
                        prefix.size >= 4 && String(prefix.take(4).toByteArray(), Charsets.US_ASCII) == "%PDF" -> "application/pdf"
                        else -> java.net.URLConnection.guessContentTypeFromStream(java.io.ByteArrayInputStream(prefix))
                    }
                }
            }.getOrNull()
        }
    }
    val available = buildList {
        if (!item.isDirectory) add(BuiltInAction("text", "Texteditor", null, Icons.Default.Description, onTextEditor, R.drawable.mt_ic_text_snippet))
        if (!item.isDirectory) add(BuiltInAction("archive", "Archiv anzeigen", null, Icons.Default.Archive, onArchiveViewer, R.drawable.mt_ic_folder_zip))
        if (item.isImageFile() || mime.startsWith("image/")) add(BuiltInAction("image", "Bildbetrachter", null, Icons.Default.Description, onImageViewer, R.drawable.mt_ic_image))
        if (item.isAudioFile() || item.isVideoFile() || mime.startsWith("audio/") || mime.startsWith("video/")) add(BuiltInAction("media", "Mediaplayer", null, Icons.Default.Description, onMediaPlayer, R.drawable.mt_ic_video))
        if (apk) add(BuiltInAction("apk", "APK-Information", null, Icons.Default.Android, onApkInfo, R.drawable.mt_ic_apk_document))
        if (split) add(BuiltInAction("split", "APKS-Funktionen", null, Icons.Default.InstallMobile, onSplitFunctions, R.drawable.mt_ic_apk_document))
        if (apk || split) add(BuiltInAction("apktool", "Dekompilieren", "apktool-a", Icons.Default.Build, onApktoolDecode, R.drawable.mt_ic_tools))
        if (ext in setOf("jks", "keystore", "p12", "pfx", "bks")) add(BuiltInAction("key", "Schlüssel importieren", null, Icons.Default.Description, onKeyImport, R.drawable.mt_ic_key))
        add(BuiltInAction("reveal", "Datei lokalisieren", null, Icons.Default.LocationOn, onRevealInPanel, R.drawable.mt_ic_folder))
    }
    val orderIndex = prefs.builtInOpenOrder.withIndex().associate { it.value to it.index }
    val actions = available.sortedBy { orderIndex[it.id] ?: Int.MAX_VALUE }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(2.dp), shadowElevation = 10.dp,
            modifier = Modifier.fillMaxWidth(0.9f).heightIn(max = 620.dp)) {
            Column(Modifier.padding(18.dp).verticalScroll(rememberScrollState())) {
                Text("Öffnen mit: ${item.name}", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.size(12.dp))
                Text("Gemeldeter MIME-Typ: $reportedMime", style = MaterialTheme.typography.bodySmall)
                Text("Erkannter MIME-Typ: ${actualMime ?: if (item.isFtp) "FTP: keine lokale Inhaltserkennung" else "nicht erkannt"}", style = MaterialTheme.typography.bodySmall)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Erkannten MIME-Typ verwenden", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                    Switch(useActualMime, { useActualMime = it; mimePrefs.edit().putBoolean("actual_mime", it).apply() })
                }
                actions.chunked(3).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        row.forEach { action ->
                            Column(Modifier.weight(1f).clickable(onClick = action.onClick).padding(vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                if (action.drawable != null) Icon(painterResource(action.drawable), null, Modifier.size(40.dp))
                                else Icon(action.icon, null, Modifier.size(40.dp))
                                Spacer(Modifier.size(6.dp))
                                Text(action.title, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("SCHLIESSEN") }
                    TextButton(onClick = { onExternalApp(if (useActualMime) actualMime ?: reportedMime else reportedMime) }) { Text("WEITERE APPS") }
                }
            }
        }
    }
}


/** File-type specific tools behind the long-press menu's Tools entry. */
@Composable
fun FileToolsDialog(
    item: FileItem,
    onDismiss: () -> Unit,
    onTextEditor: () -> Unit,
    onArchiveViewer: () -> Unit,
    onExtractArchive: () -> Unit,
    onApkInfo: () -> Unit,
    onSplitFunctions: () -> Unit,
    onApktool: () -> Unit,
) {
    val ext = item.extensionName.lowercase()
    val split = ext in setOf("apks", "apkm", "xapk", "apkx")
    val apk = ext == "apk"
    val archive = item.isArchiveFile() || ext == "jar" || apk || split
    val actions = buildList {
        if (item.isEditableTextFile()) add(BuiltInAction("text", "Texteditor", null, Icons.Default.Description, onTextEditor))
        if (archive) add(BuiltInAction("archive", "Archiv öffnen", "In diesem Panel anzeigen", Icons.Default.Archive, onArchiveViewer))
        if (archive) add(BuiltInAction("extract", "Entpacken", null, Icons.Default.Unarchive, onExtractArchive))
        if (apk) add(BuiltInAction("apk", "APK-Information", null, Icons.Default.Android, onApkInfo))
        if (split) add(BuiltInAction("split", "APKS-Funktionen", null, Icons.Default.InstallMobile, onSplitFunctions))
        if (apk || split) add(BuiltInAction("apktool", "Dekompilieren", "Apktool", Icons.Default.Build, onApktool))
    }
    MtActionDialog(
        title = "Tools",
        actions = actions.ifEmpty { listOf(BuiltInAction("none", "Keine passenden Tools", null, Icons.Default.Build, onDismiss)) },
        onDismiss = onDismiss,
    )
}

@Composable
private fun MtActionDialog(
    title: String,
    actions: List<BuiltInAction>,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurface,
            shape = RoundedCornerShape(2.dp),
            shadowElevation = 10.dp,
            modifier = Modifier.fillMaxWidth(0.86f),
        ) {
            Column(Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 8.dp)) {
                Text(title, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.size(10.dp))
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    actions.forEach { action ->
                        Row(
                            Modifier.fillMaxWidth().clickable(onClick = action.onClick).padding(vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(action.icon, contentDescription = null, modifier = Modifier.size(25.dp))
                            Spacer(Modifier.width(16.dp))
                            Column(Modifier.weight(1f)) {
                                Text(action.title, fontSize = 17.sp)
                                action.subtitle?.let {
                                    Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth()) {
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onDismiss) { Text("SCHLIESSEN") }
                }
            }
        }
    }
}
