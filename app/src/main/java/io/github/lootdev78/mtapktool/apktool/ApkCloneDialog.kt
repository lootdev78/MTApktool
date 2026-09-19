package io.github.lootdev78.mtapktool.apktool

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.lootdev78.mtapktool.feature.explorer.util.ApkArchiveReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import mt.modder.hub.apkCloner.util.ApkCloner
import java.io.File

@Composable
fun ApkCloneDialog(
    file: File,
    leftPath: String?,
    rightPath: String?,
    onDismiss: () -> Unit,
    onOutputCreated: (File) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val packageName by produceState(initialValue = "", file.absolutePath, file.lastModified()) {
        value = withContext(Dispatchers.IO) { ApkArchiveReader.read(context, file)?.packageName.orEmpty() }
    }
    var newPackage by remember(packageName) {
        mutableStateOf(if (packageName.isBlank()) "" else "$packageName.clone")
    }
    var output by remember(file.absolutePath) {
        mutableStateOf(File(file.parentFile ?: File("."), file.nameWithoutExtension + "_clone.apk").absolutePath)
    }
    var busy by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    fun setFolder(path: String?) {
        if (path.isNullOrBlank() || path.startsWith("content://")) return
        output = File(path, file.nameWithoutExtension + "_clone.apk").absolutePath
    }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(io.github.lootdev78.mtapktool.core.i18n.UiText.auto("APK klonen")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    if (packageName.isBlank()) "Paketname wird gelesen …" else "Original: $packageName",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = newPackage,
                    onValueChange = { newPackage = it.trim() },
                    label = { Text(io.github.lootdev78.mtapktool.core.i18n.UiText.auto("Neuer Paketname")) },
                    singleLine = true,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    TextButton(onClick = { setFolder(file.parentFile?.absolutePath) }, enabled = !busy) { Text(io.github.lootdev78.mtapktool.core.i18n.UiText.auto("GLEICHER")) }
                    if (!leftPath.isNullOrBlank() && !leftPath.startsWith("content://")) {
                        TextButton(onClick = { setFolder(leftPath) }, enabled = !busy) { Text(io.github.lootdev78.mtapktool.core.i18n.UiText.auto("LINKS")) }
                    }
                    if (!rightPath.isNullOrBlank() && !rightPath.startsWith("content://")) {
                        TextButton(onClick = { setFolder(rightPath) }, enabled = !busy) { Text(io.github.lootdev78.mtapktool.core.i18n.UiText.auto("RECHTS")) }
                    }
                }
                OutlinedTextField(
                    value = output,
                    onValueChange = { output = it },
                    label = { Text(io.github.lootdev78.mtapktool.core.i18n.UiText.auto("Ausgabe-APK")) },
                    singleLine = true,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (busy) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.padding(end = 10.dp))
                        Text(progress.ifBlank { "Verarbeitung …" })
                    }
                }
                errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text(io.github.lootdev78.mtapktool.core.i18n.UiText.auto("ABBRECHEN")) } },
        confirmButton = {
            TextButton(
                enabled = !busy && packageName.isNotBlank() && isPackageName(newPackage) && output.isNotBlank(),
                onClick = {
                    busy = true
                    errorMessage = null
                    scope.launch {
                        val result = withContext(Dispatchers.IO) {
                            runCatching {
                                val out = File(output)
                                out.parentFile?.mkdirs()
                                val cloner = ApkCloner(context.applicationContext, object : ApkCloner.ApkClonerCallBack {
                                    override fun onProgress(current: Int, total: Int) {
                                        val pct = if (total > 0) (current * 100 / total).coerceIn(0, 100) else 0
                                        scope.launch { progress = "$pct %" }
                                    }
                                    override fun onMessage(name: String) {
                                        scope.launch { progress = name }
                                    }
                                })
                                cloner.setPath(file.absolutePath, packageName, newPackage, out.absolutePath)
                                cloner.ProcessApk()
                                if (!out.isFile || out.length() == 0L) throw IllegalStateException("Keine Ausgabe-APK erzeugt")
                                out
                            }
                        }
                        busy = false
                        result.onSuccess { out -> onOutputCreated(out) }
                            .onFailure { errorMessage = it.message ?: it.javaClass.simpleName }
                    }
                },
            ) { Text(io.github.lootdev78.mtapktool.core.i18n.UiText.auto("KLONEN")) }
        },
    )
}

private fun isPackageName(value: String): Boolean {
    val parts = value.split('.')
    return parts.size >= 2 && parts.all { part ->
        part.isNotEmpty() && (part.first().isLetter() || part.first() == '_') && part.all { it.isLetterOrDigit() || it == '_' }
    }
}
