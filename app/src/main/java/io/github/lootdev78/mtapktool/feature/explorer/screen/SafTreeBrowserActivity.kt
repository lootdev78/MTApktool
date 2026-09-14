package io.github.lootdev78.mtapktool.feature.explorer.screen

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
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
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.lootdev78.mtapktool.core.theme.MTApktoolTheme
import io.github.lootdev78.mtapktool.feature.explorer.model.CustomLocationStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SafTreeBrowserActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val tree = intent.getStringExtra(EXTRA_TREE_URI)?.let(Uri::parse) ?: run { finish(); return }
        setContent {
            MTApktoolTheme {
                SafTreeBrowser(treeUri = tree, onClose = ::finish, onOpenFile = ::openFile)
            }
        }
    }

    private fun openFile(uri: Uri, mime: String) {
        runCatching {
            startActivity(Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mime.ifBlank { "*/*" })
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            })
        }
    }

    companion object {
        const val EXTRA_TREE_URI = "tree_uri"
    }
}

private data class SafEntry(
    val uri: Uri,
    val documentId: String,
    val name: String,
    val mime: String,
    val size: Long,
) {
    val isDirectory: Boolean get() = mime == DocumentsContract.Document.MIME_TYPE_DIR
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SafTreeBrowser(treeUri: Uri, onClose: () -> Unit, onOpenFile: (Uri, String) -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val rootId = remember(treeUri) { DocumentsContract.getTreeDocumentId(treeUri) }
    var stack by remember { mutableStateOf(listOf(rootId)) }
    var entries by remember { mutableStateOf<List<SafEntry>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    val currentId = stack.last()

    LaunchedEffect(treeUri, currentId) {
        loading = true
        entries = withContext(Dispatchers.IO) {
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, currentId)
            val result = mutableListOf<SafEntry>()
            val projection = arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
                DocumentsContract.Document.COLUMN_SIZE,
            )
            context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                val idCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
                val sizeCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
                while (cursor.moveToNext()) {
                    val id = cursor.getString(idCol)
                    val name = cursor.getString(nameCol).orEmpty()
                    val mime = cursor.getString(mimeCol).orEmpty()
                    val size = if (sizeCol >= 0 && !cursor.isNull(sizeCol)) cursor.getLong(sizeCol) else 0L
                    result += SafEntry(DocumentsContract.buildDocumentUriUsingTree(treeUri, id), id, name, mime, size)
                }
            }
            result.sortedWith(compareBy<SafEntry> { !it.isDirectory }.thenBy { it.name.lowercase() })
        }
        loading = false
    }

    fun back() {
        if (stack.size > 1) stack = stack.dropLast(1) else onClose()
    }
    BackHandler { back() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(CustomLocationStore.displayName(treeUri), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(currentId.substringAfter(':'), style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                },
                navigationIcon = { IconButton(onClick = ::back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück") } },
            )
        },
    ) { insets ->
        if (loading) {
            androidx.compose.foundation.layout.Box(Modifier.fillMaxSize().padding(insets), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(insets)) {
                items(entries, key = { it.documentId }) { item ->
                    Row(
                        Modifier.fillMaxWidth().clickable {
                            if (item.isDirectory) stack = stack + item.documentId else onOpenFile(item.uri, item.mime)
                        }.padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(if (item.isDirectory) Icons.Default.Folder else Icons.Default.Description, contentDescription = null, modifier = Modifier.size(30.dp))
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(item.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (!item.isDirectory && item.size > 0) Text("${item.size / 1024} KiB", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}
