package io.github.lootdev78.mtapktool.feature.explorer.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.weight
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
import io.github.lootdev78.mtapktool.settings.ExplorerPreferences

private data class BuiltInAction(
    val id: String,
    val title: String,
    val subtitle: String? = null,
    val icon: ImageVector,
    val onClick: () -> Unit,
)

/**
 * MT-style built-in opening chooser. It deliberately exposes only MTApktool functions that
 * actually apply to the selected type. Signing is not an opening method and is intentionally
 * absent here.
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
    onExternalApp: () -> Unit,
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
    val available = buildList {
        if (textLike) add(BuiltInAction("text", "Texteditor", "Mit MH TextEditor öffnen", Icons.Default.Description, onTextEditor))
        if (archive) add(BuiltInAction("archive", "Archiv anzeigen", "Im aktuellen Panel wie einen Ordner öffnen", Icons.Default.Archive, onArchiveViewer))
        if (apk) add(BuiltInAction("apk", "APK-Information", "App-Information und APK-Funktionen", Icons.Default.Android, onApkInfo))
        if (split) add(BuiltInAction("split", "APKS-Funktionen", "Installieren, Auswahl, AntiSplit-M → APK", Icons.Default.InstallMobile, onSplitFunctions))
        if (apk || split) add(BuiltInAction("apktool", "Dekompilieren", "Mit Apktool in ein Projekt dekompilieren", Icons.Default.Build, onApktoolDecode))
        add(BuiltInAction("reveal", "Im zuletzt verwendeten Panel anzeigen", "Datei im Explorer auswählen", Icons.Default.LocationOn, onRevealInPanel))
        add(BuiltInAction("external", "Andere App", "Android-App-Auswahl öffnen", Icons.Default.FolderOpen, onExternalApp))
    }
    val orderIndex = prefs.builtInOpenOrder.withIndex().associate { it.value to it.index }
    val actions = available.sortedBy { orderIndex[it.id] ?: Int.MAX_VALUE }
    MtOpenWithDialog(actions = actions, onDismiss = onDismiss)
}


@Composable
private fun MtOpenWithDialog(
    actions: List<BuiltInAction>,
    onDismiss: () -> Unit,
) {
    // The actual MT-style "Open with..." chooser is a compact radio list, not a settings page.
    // The callback itself closes the dialog in ExplorerScreen and executes the selected action.
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
            Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 18.dp)) {
                Text("Open with...", fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.size(10.dp))
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    actions.forEach { action ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable(onClick = action.onClick)
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = false, onClick = action.onClick)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(action.title, fontSize = 18.sp)
                                action.subtitle?.let {
                                    Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
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
