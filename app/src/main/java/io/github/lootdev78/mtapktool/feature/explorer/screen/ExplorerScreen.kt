package io.github.lootdev78.mtapktool.feature.explorer.screen

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import android.widget.Toast
import com.android.apksig.ApkVerifier
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel as composeViewModel
import androidx.navigation.NavHostController
import io.github.lootdev78.mtapktool.core.theme.MtClassicAlertDialog
import io.github.lootdev78.mtapktool.core.storage.SharedStorage
import io.github.lootdev78.mtapktool.ExternalOpenBridge
import io.github.lootdev78.mtapktool.ExplorerOutputBridge
import io.github.lootdev78.mtapktool.apktool.ApkFunctionsDialog
import io.github.lootdev78.mtapktool.apktool.ApkInfoDialog
import io.github.lootdev78.mtapktool.apktool.ApkCloneDialog
import io.github.lootdev78.mtapktool.apktool.ApktoolBuildDialog
import io.github.lootdev78.mtapktool.apktool.ApktoolCliDialog
import io.github.lootdev78.mtapktool.apktool.ApktoolDecodeDialog
import io.github.lootdev78.mtapktool.apktool.ApktoolFrameworkImportDialog
import io.github.lootdev78.mtapktool.apktool.ApktoolJobOutputDialog
import io.github.lootdev78.mtapktool.apktool.ApktoolJobsViewModel
import io.github.lootdev78.mtapktool.apktool.ApktoolSettingsDialog
import io.github.lootdev78.mtapktool.apktool.ApktoolTaskPanel
import io.github.lootdev78.mtapktool.apktool.isApkLike
import io.github.lootdev78.mtapktool.apktool.isApktoolProject
import io.github.lootdev78.mtapktool.apktool.SplitArchiveSupport
import io.github.lootdev78.mtapktool.apktool.SplitPackageDialog
import io.github.lootdev78.mtapktool.archive.ArchiveActionDialog
import io.github.lootdev78.mtapktool.archive.ArchiveCreateDialog
import io.github.lootdev78.mtapktool.archive.ArchiveEngine
import io.github.lootdev78.mtapktool.archive.ArchiveExtractDialog
import io.github.lootdev78.mtapktool.feature.editor.FileInfoDialog
import io.github.lootdev78.mtapktool.feature.explorer.component.ClassicFilePane
import io.github.lootdev78.mtapktool.feature.explorer.component.EditHiddenFilesDialog
import io.github.lootdev78.mtapktool.feature.explorer.component.FileFilterDialog
import io.github.lootdev78.mtapktool.feature.explorer.component.HiddenFilesDialog
import io.github.lootdev78.mtapktool.feature.explorer.component.SortFilesDialog
import io.github.lootdev78.mtapktool.feature.explorer.component.fileFilterLabel
import io.github.lootdev78.mtapktool.feature.explorer.component.CustomCreateItemDialog
import io.github.lootdev78.mtapktool.feature.explorer.component.FileContextMenuDialog
import io.github.lootdev78.mtapktool.feature.explorer.component.FileConflictDialog
import io.github.lootdev78.mtapktool.feature.explorer.component.BuiltInOpenDialog
import io.github.lootdev78.mtapktool.feature.explorer.component.FileToolsDialog
import io.github.lootdev78.mtapktool.feature.explorer.component.RenameDialog
import io.github.lootdev78.mtapktool.feature.explorer.component.GoToPathDialog
import io.github.lootdev78.mtapktool.feature.explorer.component.SelectionBottomBar
import io.github.lootdev78.mtapktool.feature.explorer.component.BookmarkBottomSheet
import io.github.lootdev78.mtapktool.feature.explorer.component.SideBar
import io.github.lootdev78.mtapktool.feature.explorer.model.FileItem
import io.github.lootdev78.mtapktool.feature.explorer.saf.CustomLocation
import io.github.lootdev78.mtapktool.feature.explorer.saf.CustomLocationStore
import io.github.lootdev78.mtapktool.feature.explorer.saf.SafFileSystem
import io.github.lootdev78.mtapktool.feature.explorer.state.FileFilter
import io.github.lootdev78.mtapktool.feature.explorer.state.isArchiveFile
import io.github.lootdev78.mtapktool.feature.explorer.state.isAudioFile
import io.github.lootdev78.mtapktool.feature.explorer.state.isVideoFile
import io.github.lootdev78.mtapktool.feature.explorer.state.isEditableTextFile
import io.github.lootdev78.mtapktool.feature.explorer.state.isImageFile
import io.github.lootdev78.mtapktool.feature.explorer.viewmodel.ActivePane
import io.github.lootdev78.mtapktool.feature.explorer.viewmodel.ArchiveUpdateDecision
import io.github.lootdev78.mtapktool.feature.explorer.viewmodel.ExplorerViewModel
import io.github.lootdev78.mtapktool.feature.explorer.viewmodel.FileConflictAction
import io.github.lootdev78.mtapktool.feature.explorer.util.FileOpener
import io.github.lootdev78.mtapktool.settings.AppSettingsDialog
import io.github.lootdev78.mtapktool.settings.SettingsPage
import io.github.lootdev78.mtapktool.settings.ExplorerPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import modder.hub.editor.MainActivity as MhTextEditorActivity

@Composable
fun ExplorerScreen(
    navController: NavHostController,
    viewModel: ExplorerViewModel = composeViewModel()
) {
    val leftState by viewModel.leftPaneState.collectAsState()
    val rightState by viewModel.rightPaneState.collectAsState()
    val activePane by viewModel.activePane.collectAsState()
    val externalOpenRequest by ExternalOpenBridge.request.collectAsState()
    val pendingToolOutput by ExplorerOutputBridge.outputPath.collectAsState()
    val apktoolJobsViewModel: ApktoolJobsViewModel = composeViewModel()
    val apktoolJobs by apktoolJobsViewModel.jobs.collectAsState()
    val archiveTasks by viewModel.archiveTasks.collectAsState()
    var observedSuccessfulJobs by remember { mutableStateOf<Set<String>>(emptySet()) }
    var jobPaneById by remember { mutableStateOf<Map<String, ActivePane>>(emptyMap()) }

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    val rootPath = SharedStorage.primaryRoot().absolutePath
    val activeState = if (activePane == ActivePane.LEFT) leftState else rightState
    val context = navController.context
    val lifecycleOwner = LocalLifecycleOwner.current
    ExplorerPreferences.init(context)
    val explorerPrefs by ExplorerPreferences.state.collectAsState()
    val fileConflict by viewModel.fileConflict.collectAsState()
    val archiveUpdateRequest by viewModel.archiveUpdateRequest.collectAsState()
    val archivePasswordRequest by viewModel.archivePasswordRequest.collectAsState()

    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshMountedArchiveStates()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val bookmarkPreferences = remember(context) {
        context.getSharedPreferences("explorer_bookmarks", Context.MODE_PRIVATE)
    }
    var bookmarks by remember(bookmarkPreferences) {
        mutableStateOf(bookmarkPreferences.getStringSet("paths", emptySet()).orEmpty().toList().sorted())
    }
    var showBookmarkSheet by remember { mutableStateOf(false) }
    var showSelectionMore by remember { mutableStateOf(false) }
    var customLocations by remember(context) { mutableStateOf(CustomLocationStore.load(context)) }
    var locationToEdit by remember { mutableStateOf<CustomLocation?>(null) }
    var locationEditName by remember { mutableStateOf("") }
    val addLocationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            runCatching { CustomLocationStore.add(context, uri) }
                .onSuccess { location ->
                    customLocations = CustomLocationStore.load(context)
                    viewModel.openCustomLocation(activePane, CustomLocationStore.rootDocumentUri(location).toString(), location.name)
                    locationToEdit = location
                    locationEditName = location.name
                }
                .onFailure { Toast.makeText(context, "Storage permission failed: ${it.message}", Toast.LENGTH_LONG).show() }
        }
    }

    LaunchedEffect(viewModel, context) {
        viewModel.operationMessages.collect { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(apktoolJobs) {
        val successful = apktoolJobs.asSequence().filter { it.status == "SUCCEEDED" }.map { it.id }.toSet()
        val newlyFinished = successful - observedSuccessfulJobs
        if (newlyFinished.isNotEmpty()) {
            observedSuccessfulJobs = observedSuccessfulJobs + newlyFinished
            newlyFinished.forEach { id ->
                val job = apktoolJobs.firstOrNull { it.id == id }
                val pane = jobPaneById[id] ?: activePane
                val output = job?.output
                if (!output.isNullOrBlank() && File(output).exists()) viewModel.revealOutput(pane, output)
                else viewModel.refreshDirectory(pane)
            }
        }
    }

    // State Variables
    var showContextMenu by remember { mutableStateOf(false) }
    var showBuiltInOpen by remember { mutableStateOf(false) }
    var showFileTools by remember { mutableStateOf(false) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var showGoToPathDialog by remember { mutableStateOf(false) }
    var goToPane by remember { mutableStateOf<ActivePane?>(null) }
    var showPropertyDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var targetItem by remember { mutableStateOf<FileItem?>(null) }
    var isSearching by remember { mutableStateOf(false) }
    var showApkInfo by remember { mutableStateOf(false) }
    var apkInfoPane by remember { mutableStateOf(ActivePane.LEFT) }
    var showSplitPackage by remember { mutableStateOf(false) }
    var splitPackagePane by remember { mutableStateOf(ActivePane.LEFT) }
    var showApkFunctions by remember { mutableStateOf(false) }
    var showApkClone by remember { mutableStateOf(false) }
    var showApktoolDecode by remember { mutableStateOf(false) }
    var showFrameworkImport by remember { mutableStateOf(false) }
    var showApktoolBuild by remember { mutableStateOf(false) }
    var showApktoolSettings by remember { mutableStateOf(false) }
    var showAppSettings by remember { mutableStateOf(false) }
    var appSettingsInitialPage by remember { mutableStateOf(SettingsPage.ROOT) }
    var showTaskPanel by remember { mutableStateOf(false) }
    var selectedJobId by remember { mutableStateOf<String?>(null) }
    var showApktoolCli by remember { mutableStateOf(false) }
    var showApktoolOptions by remember { mutableStateOf(false) }
    var apktoolTarget by remember { mutableStateOf<File?>(null) }
    var showArchiveDialog by remember { mutableStateOf(false) }
    var archiveSources by remember { mutableStateOf<List<File>>(emptyList()) }
    var archivePane by remember { mutableStateOf(ActivePane.LEFT) }
    var showArchiveActions by remember { mutableStateOf(false) }
    var showArchiveExtract by remember { mutableStateOf(false) }
    var archiveTarget by remember { mutableStateOf<File?>(null) }
    var archiveActionPane by remember { mutableStateOf(ActivePane.LEFT) }
    var archiveActionPassword by remember { mutableStateOf("") }
    var showHiddenFiles by remember { mutableStateOf(false) }
    var showEditHiddenFiles by remember { mutableStateOf(false) }
    var showSortFiles by remember { mutableStateOf(false) }
    var showSortManage by remember { mutableStateOf(false) }
    var showFilterFiles by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var deletePane by remember { mutableStateOf(ActivePane.LEFT) }
    var recycleOnDelete by remember { mutableStateOf(false) }

    fun requestDelete(pane: ActivePane) {
        deletePane = pane
        recycleOnDelete = explorerPrefs.recycleBinEnabled && explorerPrefs.moveToRecycleBinByDefault
        showDeleteConfirm = true
    }

    val hasSelectedItems = activeState.selectedPaths.isNotEmpty()
    val canNavigateBack = viewModel.canNavigateUp(activePane)

    fun openInTextEditor(item: FileItem) {
        val intent = Intent(context, MhTextEditorActivity::class.java).apply {
            if (item.isSaf) {
                putExtra("uri", item.path)
                putExtra("name", item.name)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            } else {
                putExtra("path", item.path)
                putExtra("name", item.name)
            }
        }
        runCatching { context.startActivity(intent) }
            .onFailure { Toast.makeText(context, it.message ?: "Editor konnte nicht geöffnet werden", Toast.LENGTH_SHORT).show() }
    }

    fun materializeForTool(item: FileItem, pane: ActivePane, onReady: (File) -> Unit) {
        if (!item.isSaf) {
            onReady(File(item.path))
            return
        }
        scope.launch {
            viewModel.setPaneBusy(pane, true, "Bereite ${item.name} vor")
            val result = withContext(Dispatchers.IO) {
                runCatching { SafFileSystem.materializeToCache(context, Uri.parse(item.path), item.name) }
            }
            viewModel.setPaneBusy(pane, false)
            result.onSuccess(onReady)
                .onFailure { Toast.makeText(context, it.message ?: "Datei konnte nicht geöffnet werden", Toast.LENGTH_SHORT).show() }
        }
    }

    fun revealIncomingInLastPane(item: FileItem) {
        if (!item.isSaf) {
            viewModel.revealOutput(activePane, item.path)
            return
        }
        val uri = Uri.parse(item.path)
        val local = runCatching {
            if (uri.authority == "com.android.externalstorage.documents") {
                val id = DocumentsContract.getDocumentId(uri)
                val parts = id.split(':', limit = 2)
                if (parts.firstOrNull().equals("primary", ignoreCase = true)) {
                    File(SharedStorage.primaryRoot(), parts.getOrElse(1) { "" })
                } else null
            } else null
        }.getOrNull()
        if (local != null && local.exists()) {
            viewModel.revealOutput(activePane, local.absolutePath)
        } else {
            Toast.makeText(context, "Der Original-Speicherort dieser URI ist für Android nicht direkt auflösbar.", Toast.LENGTH_LONG).show()
        }
    }

    LaunchedEffect(pendingToolOutput) {
        val output = pendingToolOutput ?: return@LaunchedEffect
        ExplorerOutputBridge.clear()
        if (File(output).exists()) viewModel.revealOutput(activePane, output)
    }

    LaunchedEffect(externalOpenRequest?.uri) {
        val request = externalOpenRequest ?: return@LaunchedEffect
        val uri = Uri.parse(request.uri)
        val item = if (uri.scheme.equals("file", ignoreCase = true)) {
            val file = uri.path?.let(::File)
            file?.takeIf { it.exists() }?.let(::FileItem)
        } else {
            var name = uri.lastPathSegment?.substringAfterLast('/')?.ifBlank { "Datei" } ?: "Datei"
            var size = 0L
            runCatching {
                context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                        if (nameIndex >= 0 && !cursor.isNull(nameIndex)) name = cursor.getString(nameIndex)
                        if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) size = cursor.getLong(sizeIndex)
                    }
                }
            }
            FileItem(
                file = File(name),
                safUri = uri.toString(),
                displayName = name,
                directoryOverride = false,
                sizeOverride = size,
                mimeType = request.mimeType ?: context.contentResolver.getType(uri),
            )
        }
        ExternalOpenBridge.clear()
        if (item != null) {
            targetItem = item
            showBuiltInOpen = true
        } else {
            Toast.makeText(context, "Datei konnte nicht geöffnet werden", Toast.LENGTH_SHORT).show()
        }
    }

    fun openSafItem(pane: ActivePane, item: FileItem) {
        if (item.isDirectory) {
            viewModel.loadDirectory(pane, item.path)
            return
        }
        val ext = item.extensionName
        if (item.isEditableTextFile()) {
            openInTextEditor(item)
            return
        }
        if (ext in setOf("apk", "apks", "apkm", "xapk", "apkx") || item.isArchiveFile()) {
            scope.launch {
                viewModel.setPaneBusy(pane, true, "Öffne ${item.name}", 0)
                val result = withContext(Dispatchers.IO) {
                    runCatching { SafFileSystem.materializeToCache(context, Uri.parse(item.path), item.name) }
                }
                result.onSuccess { local ->
                    when {
                        ext == "apk" -> {
                            viewModel.setPaneBusy(pane, false)
                            apktoolTarget = local
                            targetItem = item
                            apkInfoPane = pane
                            showApkInfo = true
                        }
                        ext in setOf("apks", "apkm", "xapk", "apkx") -> {
                            viewModel.setPaneBusy(pane, false)
                            apktoolTarget = local
                            targetItem = item
                            splitPackagePane = pane
                            showSplitPackage = true
                        }
                        ArchiveEngine.supports(local) -> viewModel.openArchive(pane, local)
                        else -> {
                            viewModel.setPaneBusy(pane, false)
                            FileOpener.openUri(context, Uri.parse(item.path), item.name, item.mimeType)
                        }
                    }
                }.onFailure {
                    viewModel.setPaneBusy(pane, false)
                    Toast.makeText(context, it.message ?: "Open failed", Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            FileOpener.openUri(context, Uri.parse(item.path), item.name, item.mimeType)
        }
    }

    // Dialogs
    if (showContextMenu && targetItem != null) {
        FileContextMenuDialog(
            targetItem = targetItem,
            activePane = activePane,
            onDismissRequest = { showContextMenu = false },
            onCopy = {
                targetItem?.let { item ->
                    viewModel.ensureSelected(activePane, item.path)
                    viewModel.copySelectedToOppositePane(activePane)
                }
                showContextMenu = false
            },
            onMove = {
                targetItem?.let { item ->
                    viewModel.ensureSelected(activePane, item.path)
                    viewModel.moveSelectedToOppositePane(activePane)
                }
                showContextMenu = false
            },
            onRename = {
                showRenameDialog = true
                showContextMenu = false
            },
            onDelete = {
                targetItem?.let { item ->
                    viewModel.ensureSelected(activePane, item.path)
                    requestDelete(activePane)
                }
                showContextMenu = false
            },
            onTools = {
                showContextMenu = false
                showFileTools = true
            },
            onCompress = {
                targetItem?.let { item ->
                    materializeForTool(item, activePane) { file ->
                        archiveSources = listOf(file)
                        archivePane = activePane
                        showArchiveDialog = true
                    }
                }
                showContextMenu = false
            },
            onProperty = {
                showPropertyDialog = true
                showContextMenu = false
            },
            onShare = {
                targetItem?.let { item ->
                    runCatching {
                        val uri = if (item.isSaf) Uri.parse(item.path) else FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", File(item.path))
                        val mimeType = item.mimeType ?: MimeTypeMap.getSingleton().getMimeTypeFromExtension(item.extensionName) ?: "*/*"
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = mimeType
                            putExtra(Intent.EXTRA_STREAM, uri)
                            clipData = ClipData.newRawUri(item.name, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(intent, "Datei teilen"))
                    }.onFailure { error -> Toast.makeText(context, "Teilen fehlgeschlagen: ${error.message}", Toast.LENGTH_SHORT).show() }
                }
                showContextMenu = false
            },
            onOpenWith = {
                showContextMenu = false
                showBuiltInOpen = true
            },
            onAddBookmark = {
                targetItem?.let { item ->
                    val updated = (bookmarks + item.path).distinct().sorted()
                    bookmarks = updated
                    bookmarkPreferences.edit().putStringSet("paths", updated.toSet()).apply()
                    Toast.makeText(context, "Lesezeichen hinzugefügt", Toast.LENGTH_SHORT).show()
                }
                showContextMenu = false
            },
        )
    }

    if (showBuiltInOpen && targetItem != null) {
        val item = targetItem!!
        BuiltInOpenDialog(
            item = item,
            onDismiss = { showBuiltInOpen = false },
            onTextEditor = {
                showBuiltInOpen = false
                openInTextEditor(item)
            },
            onArchiveViewer = {
                showBuiltInOpen = false
                materializeForTool(item, activePane) { file ->
                    if (ArchiveEngine.supports(file)) viewModel.openArchive(activePane, file)
                    else Toast.makeText(context, "Kein unterstütztes Archiv", Toast.LENGTH_SHORT).show()
                }
            },
            onApkInfo = {
                showBuiltInOpen = false
                materializeForTool(item, activePane) { file ->
                    apktoolTarget = file
                    apkInfoPane = activePane
                    showApkInfo = true
                }
            },
            onSplitFunctions = {
                showBuiltInOpen = false
                materializeForTool(item, activePane) { file ->
                    apktoolTarget = file
                    splitPackagePane = activePane
                    showSplitPackage = true
                }
            },
            onApktoolDecode = {
                showBuiltInOpen = false
                materializeForTool(item, activePane) { file ->
                    apktoolTarget = file
                    showApktoolDecode = true
                }
            },
            onRevealInPanel = {
                showBuiltInOpen = false
                revealIncomingInLastPane(item)
            },
            onExternalApp = {
                showBuiltInOpen = false
                if (item.isSaf) FileOpener.openUri(context, Uri.parse(item.path), item.name, item.mimeType)
                else FileOpener.openFile(context, File(item.path))
            },
        )
    }

    if (showFileTools && targetItem != null) {
        val item = targetItem!!
        FileToolsDialog(
            item = item,
            onDismiss = { showFileTools = false },
            onTextEditor = {
                showFileTools = false
                openInTextEditor(item)
            },
            onArchiveViewer = {
                showFileTools = false
                materializeForTool(item, activePane) { file ->
                    if (ArchiveEngine.supports(file)) viewModel.openArchive(activePane, file)
                }
            },
            onExtractArchive = {
                showFileTools = false
                materializeForTool(item, activePane) { file ->
                    if (ArchiveEngine.supports(file)) {
                        archiveTarget = file
                        archiveActionPane = activePane
                        archiveActionPassword = ""
                        showArchiveExtract = true
                    }
                }
            },
            onApkInfo = {
                showFileTools = false
                materializeForTool(item, activePane) { file ->
                    apktoolTarget = file
                    apkInfoPane = activePane
                    showApkInfo = true
                }
            },
            onSplitFunctions = {
                showFileTools = false
                materializeForTool(item, activePane) { file ->
                    apktoolTarget = file
                    splitPackagePane = activePane
                    showSplitPackage = true
                }
            },
            onApktool = {
                showFileTools = false
                materializeForTool(item, activePane) { file ->
                    apktoolTarget = file
                    if (isApktoolProject(file)) showApktoolBuild = true else showApktoolDecode = true
                }
            },
        )
    }

    if (showPropertyDialog && targetItem != null) {
        val item = targetItem!!
        if (item.isSaf) {
            MtClassicAlertDialog(
                onDismissRequest = { showPropertyDialog = false },
                title = { Text(item.name) },
                text = {
                    Column {
                        Text(if (item.isDirectory) "Folder" else "File")
                        if (!item.isDirectory) Text("Size: ${item.sizeText}")
                        Text("Storage: Android document tree (read/write)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                confirmButton = { TextButton(onClick = { showPropertyDialog = false }) { Text("OK") } },
            )
        } else {
            FileInfoDialog(
                file = File(item.path),
                onDismiss = { showPropertyDialog = false }
            )
        }
    }

    if (showRenameDialog && targetItem != null) {
        RenameDialog(
            initialName = targetItem!!.name,
            onDismiss = { showRenameDialog = false },
            onRename = { newName ->
                viewModel.renameItem(activePane, targetItem!!.path, newName)
                showRenameDialog = false
            }
        )
    }

    if (showCreateDialog) {
        CustomCreateItemDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = { name, isFolder ->
                val data = viewModel.createNewItem(activePane, name, isFolder)
                println(data)
                Toast.makeText(navController.context, data, Toast.LENGTH_SHORT).show()
                showCreateDialog = false
            }
        )
    }

    if (showGoToPathDialog) {
        val targetPane = goToPane ?: activePane
        val targetState = if (targetPane == ActivePane.LEFT) leftState else rightState
        GoToPathDialog(
            initialPath = targetState.displayPath,
            onDismiss = { showGoToPathDialog = false; goToPane = null },
            onGo = { path ->
                viewModel.setActive(targetPane)
                viewModel.navigateToDisplayPath(targetPane, path)
                showGoToPathDialog = false
                goToPane = null
            }
        )
    }

    if (showHiddenFiles) {
        HiddenFilesDialog(
            showSystemHidden = activeState.showSystemHidden,
            showManuallyHidden = activeState.showManuallyHidden,
            selectedCount = activeState.selectedPaths.size,
            manualHiddenCount = activeState.manuallyHiddenPaths.size,
            onShowSystemHidden = { viewModel.setShowSystemHidden(activePane, it) },
            onShowManuallyHidden = { viewModel.setShowManuallyHidden(activePane, it) },
            onHideSelected = { viewModel.hideSelectedManually(activePane) },
            onEditHidden = {
                showHiddenFiles = false
                showEditHiddenFiles = true
            },
            onDismiss = { showHiddenFiles = false },
        )
    }

    if (showEditHiddenFiles) {
        EditHiddenFilesDialog(
            paths = activeState.manuallyHiddenPaths.sorted(),
            onRemove = viewModel::unhideManualPath,
            onClear = viewModel::clearManualHidden,
            onDismiss = { showEditHiddenFiles = false },
        )
    }

    if (showSortFiles) {
        SortFilesDialog(
            windowLabel = if (activePane == ActivePane.LEFT) "Linkes Fenster" else "Rechtes Fenster",
            current = activeState.sortSpec,
            onManage = {
                showSortFiles = false
                showSortManage = true
            },
            onApply = { spec, onlyFolder -> viewModel.setSort(activePane, spec, onlyFolder) },
            onDismiss = { showSortFiles = false },
        )
    }

    if (showSortManage) {
        MtClassicAlertDialog(
            onDismissRequest = { showSortManage = false },
            title = { Text("Sortierung verwalten") },
            text = { Text("Ordnerspezifische Sortierungen für ${if (activePane == ActivePane.LEFT) "das linke" else "das rechte"} Fenster zurücksetzen?") },
            dismissButton = { TextButton(onClick = { showSortManage = false }) { Text("ABBRECHEN") } },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearFolderSortOverrides(activePane)
                    showSortManage = false
                }) { Text("ZURÜCKSETZEN") }
            },
        )
    }

    if (showFilterFiles) {
        FileFilterDialog(
            current = activeState.filter,
            onApply = { viewModel.setFilter(activePane, it) },
            onDismiss = { showFilterFiles = false },
        )
    }

    if (showArchiveActions) {
        archiveTarget?.takeIf { ArchiveEngine.supports(it) }?.let { file ->
            ArchiveActionDialog(
                archive = file,
                onDismiss = { showArchiveActions = false },
                onOpen = { password ->
                    archiveActionPassword = password
                    showArchiveActions = false
                    viewModel.openArchive(archiveActionPane, file, password)
                },
                onExtract = { password ->
                    archiveActionPassword = password
                    showArchiveActions = false
                    showArchiveExtract = true
                },
            )
        } ?: run { showArchiveActions = false }
    }

    if (showArchiveExtract) {
        archiveTarget?.takeIf { ArchiveEngine.supports(it) }?.let { file ->
            val sourceState = if (archiveActionPane == ActivePane.LEFT) leftState else rightState
            val otherState = if (archiveActionPane == ActivePane.LEFT) rightState else leftState
            ArchiveExtractDialog(
                archive = file,
                currentDirectory = File(sourceState.currentPath),
                oppositeDirectory = File(otherState.currentPath),
                initialPassword = archiveActionPassword,
                onDismiss = { showArchiveExtract = false },
                onExtract = { request ->
                    viewModel.extractArchive(archiveActionPane, request)
                    showArchiveExtract = false
                },
            )
        } ?: run { showArchiveExtract = false }
    }

    if (showArchiveDialog && archiveSources.isNotEmpty()) {
        val sourceState = if (archivePane == ActivePane.LEFT) leftState else rightState
        val otherState = if (archivePane == ActivePane.LEFT) rightState else leftState
        ArchiveCreateDialog(
            sources = archiveSources,
            currentDirectory = File(sourceState.currentPath),
            oppositeDirectory = File(otherState.currentPath),
            onDismiss = { showArchiveDialog = false },
            onCreate = { request ->
                viewModel.createArchive(archivePane, request)
                showArchiveDialog = false
            },
        )
    }

    if (showApkInfo) {
        apktoolTarget?.takeIf { it.isFile && it.extension.equals("apk", ignoreCase = true) }?.let { file ->
            ApkInfoDialog(
                file = file,
                panelLabel = if (apkInfoPane == ActivePane.LEFT) "Links" else "Rechts",
                onDismiss = { showApkInfo = false },
                onFunctions = {
                    showApkInfo = false
                    showApkFunctions = true
                },
                onView = {
                    showApkInfo = false
                    viewModel.openArchive(apkInfoPane, file)
                },
                onInstall = {
                    installApk(context, file)
                    showApkInfo = false
                },
            )
        } ?: run { showApkInfo = false }
    }

    if (showSplitPackage) {
        apktoolTarget?.takeIf { SplitArchiveSupport.isSplitArchive(it) }?.let { file ->
            SplitPackageDialog(
                file = file,
                sourcePane = splitPackagePane,
                leftPath = leftState.currentPath,
                rightPath = rightState.currentPath,
                onDismiss = { showSplitPackage = false },
                onDecode = {
                    showSplitPackage = false
                    showApktoolDecode = true
                },
                onOutputCreated = { output, pane ->
                    showSplitPackage = false
                    viewModel.revealOutput(pane, output.absolutePath)
                },
            )
        } ?: run { showSplitPackage = false }
    }

    if (showApkFunctions) {
        apktoolTarget?.takeIf { it.isFile && it.extension.equals("apk", ignoreCase = true) }?.let { file ->
            ApkFunctionsDialog(
                file = file,
                onDismiss = { showApkFunctions = false },
                onDecode = {
                    showApkFunctions = false
                    showApktoolDecode = true
                },
                onImportFramework = {
                    showApkFunctions = false
                    showFrameworkImport = true
                },
                onClone = {
                    showApkFunctions = false
                    showApkClone = true
                },
                onFileInfo = {
                    targetItem = FileItem(file)
                    showApkFunctions = false
                    showPropertyDialog = true
                },
                onOpenWith = {
                    showApkFunctions = false
                    targetItem = FileItem(file)
                    showBuiltInOpen = true
                },
                onShare = {
                    showApkFunctions = false
                    shareFile(context, file)
                },
            )
        } ?: run { showApkFunctions = false }
    }

    if (showApkClone) {
        apktoolTarget?.takeIf { it.isFile && it.extension.equals("apk", ignoreCase = true) }?.let { file ->
            ApkCloneDialog(
                file = file,
                leftPath = leftState.currentPath,
                rightPath = rightState.currentPath,
                onDismiss = { showApkClone = false },
                onOutputCreated = { output ->
                    showApkClone = false
                    viewModel.revealOutput(apkInfoPane, output.absolutePath)
                },
            )
        } ?: run { showApkClone = false }
    }

    if (showFrameworkImport) {
        apktoolTarget?.takeIf { it.isFile && it.extension.equals("apk", ignoreCase = true) }?.let { file ->
            ApktoolFrameworkImportDialog(
                file = file,
                onDismiss = { showFrameworkImport = false },
                onJobQueued = { jobId -> jobPaneById = jobPaneById + (jobId to activePane); selectedJobId = jobId },
            )
        } ?: run { showFrameworkImport = false }
    }

    if (showApktoolDecode) {
        apktoolTarget?.takeIf(::isApkLike)?.let { file ->
            ApktoolDecodeDialog(file = file, onDismiss = { showApktoolDecode = false }, onJobQueued = { jobId -> jobPaneById = jobPaneById + (jobId to activePane); selectedJobId = jobId })
        } ?: run { showApktoolDecode = false }
    }

    if (showApktoolBuild) {
        apktoolTarget?.takeIf(::isApktoolProject)?.let { project ->
            ApktoolBuildDialog(project = project, onDismiss = { showApktoolBuild = false }, onJobQueued = { jobId -> jobPaneById = jobPaneById + (jobId to activePane); selectedJobId = jobId })
        } ?: run { showApktoolBuild = false }
    }

    if (showApktoolSettings) {
        ApktoolSettingsDialog(onDismiss = { showApktoolSettings = false })
    }

    if (showAppSettings) {
        AppSettingsDialog(
            onDismiss = { showAppSettings = false },
            initialPage = appSettingsInitialPage,
            onJobQueued = { jobId -> jobPaneById = jobPaneById + (jobId to activePane); selectedJobId = jobId },
        )
    }

    if (showApktoolCli) {
        ApktoolCliDialog(onDismiss = { showApktoolCli = false }, onJobQueued = { jobId -> jobPaneById = jobPaneById + (jobId to activePane); selectedJobId = jobId })
    }

    selectedJobId?.let { jobId ->
        ApktoolJobOutputDialog(
            jobId = jobId,
            job = apktoolJobs.firstOrNull { it.id == jobId },
            onHide = { selectedJobId = null },
            onCancel = apktoolJobsViewModel::cancel,
        )
    }

    locationToEdit?.let { location ->
        MtClassicAlertDialog(
            onDismissRequest = { locationToEdit = null },
            title = { Text("Speicherort") },
            text = {
                Column {
                    Text("Name", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(
                        value = locationEditName,
                        onValueChange = { locationEditName = it },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        text = "Lese- und Schreibzugriff ist aktiv. Entfernen löscht nur diesen Eintrag und gibt die gespeicherte Android-Berechtigung frei.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    val prefix = location.treeUri
                    CustomLocationStore.remove(context, location.id)
                    customLocations = CustomLocationStore.load(context)
                    if (leftState.currentPath.startsWith(prefix)) viewModel.navigateToDirectPath(ActivePane.LEFT, rootPath)
                    if (rightState.currentPath.startsWith(prefix)) viewModel.navigateToDirectPath(ActivePane.RIGHT, rootPath)
                    locationToEdit = null
                }) { Text("ENTFERNEN") }
            },
            confirmButton = {
                Row {
                    TextButton(onClick = { locationToEdit = null }) { Text("ABBRECHEN") }
                    TextButton(onClick = {
                        CustomLocationStore.rename(context, location.id, locationEditName)
                        customLocations = CustomLocationStore.load(context)
                        locationToEdit = null
                    }) { Text("SPEICHERN") }
                }
            },
        )
    }

    fileConflict?.let { request ->
        FileConflictDialog(
            request = request,
            onResolve = { action, applyAll -> viewModel.resolveFileConflict(action, applyAll) },
        )
    }

    archivePasswordRequest?.let { request ->
        var password by remember(request.archive.absolutePath, request.purpose) { mutableStateOf("") }
        MtClassicAlertDialog(
            onDismissRequest = viewModel::cancelArchivePasswordRequest,
            title = { Text("Passwort") },
            text = {
                Column {
                    Text(request.archive.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(8.dp))
                    Text("Dieses Archiv benötigt ein Passwort.", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        singleLine = true,
                        label = { Text("Passwort") },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                    request.message?.takeIf { it.isNotBlank() }?.let {
                        Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            },
            dismissButton = { TextButton(onClick = viewModel::cancelArchivePasswordRequest) { Text("ABBRECHEN") } },
            confirmButton = {
                TextButton(enabled = password.isNotBlank(), onClick = { viewModel.submitArchivePassword(password) }) { Text("OK") }
            },
        )
    }

    archiveUpdateRequest?.let { request ->
        MtClassicAlertDialog(
            onDismissRequest = { viewModel.resolveArchiveUpdate(ArchiveUpdateDecision.CANCEL) },
            title = { Text("Archiv aktualisieren?") },
            text = {
                Column {
                    Text("${request.archiveName} wurde geändert.")
                    if (request.dirtyEntries.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text("${request.dirtyEntries.size} geänderte Einträge", style = MaterialTheme.typography.bodySmall)
                        request.dirtyEntries.take(4).forEach { Text("• $it", style = MaterialTheme.typography.bodySmall, maxLines = 1) }
                    }
                    if (request.nestedDepth > 0) {
                        Spacer(Modifier.height(8.dp))
                        Text("Nach dem Aktualisieren wird auch das äußere Archiv als geändert markiert.", style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { viewModel.resolveArchiveUpdate(ArchiveUpdateDecision.DISCARD) }) { Text("VERWERFEN") }
                    TextButton(onClick = { viewModel.resolveArchiveUpdate(ArchiveUpdateDecision.CANCEL) }) { Text("ABBRECHEN") }
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.resolveArchiveUpdate(ArchiveUpdateDecision.UPDATE) }) { Text("AKTUALISIEREN") }
            },
        )
    }

    if (showDeleteConfirm) {
        val state = if (deletePane == ActivePane.LEFT) leftState else rightState
        val count = state.selectedPaths.size
        MtClassicAlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Löschen") },
            text = {
                Column {
                    Text(if (count == 1) "Ausgewähltes Element löschen?" else "$count ausgewählte Elemente löschen?")
                    if (explorerPrefs.recycleBinEnabled) {
                        Row(
                            Modifier.fillMaxWidth().clickable { recycleOnDelete = !recycleOnDelete }.padding(top = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(recycleOnDelete, { recycleOnDelete = it })
                            Text("In Papierkorb verschieben")
                        }
                    }
                    if (!recycleOnDelete && explorerPrefs.showDeletionWarning) {
                        Text(
                            "Die Dateien werden endgültig gelöscht.",
                            modifier = Modifier.padding(top = 8.dp),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("ABBRECHEN") } },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    viewModel.deleteSelected(deletePane, recycleOverride = recycleOnDelete)
                }) { Text("OK") }
            },
        )
    }

    BackHandler(enabled = showTaskPanel || selectedJobId != null || canNavigateBack || isSearching) {
        if (selectedJobId != null) {
            selectedJobId = null
        } else if (showTaskPanel) {
            showTaskPanel = false
        } else if (isSearching) {
            isSearching = false
            viewModel.clearSearch(activePane)
        } else {
            viewModel.navigateUp(activePane)
        }
    }

    BoxWithConstraints {
        val drawerWidth = maxWidth * 0.7f
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                    SideBar(
                        drawerWidth = drawerWidth,
                        viewModel = viewModel,
                        bookmarks = bookmarks,
                        customLocations = customLocations,
                        onBookmarkClick = { path -> viewModel.navigateToDirectPath(activePane, path) },
                        onCustomLocationClick = { location ->
                            viewModel.openCustomLocation(activePane, CustomLocationStore.rootDocumentUri(location).toString(), location.name)
                        },
                        onCustomLocationLongClick = { location ->
                            locationToEdit = location
                            locationEditName = location.name
                        },
                        onCustomLocationDelete = { location ->
                            val prefix = location.treeUri
                            CustomLocationStore.remove(context, location.id)
                            customLocations = CustomLocationStore.load(context)
                            if (leftState.currentPath.startsWith(prefix)) viewModel.navigateToDirectPath(ActivePane.LEFT, rootPath)
                            if (rightState.currentPath.startsWith(prefix)) viewModel.navigateToDirectPath(ActivePane.RIGHT, rootPath)
                        },
                        onCustomLocationHide = { location, hidden ->
                            CustomLocationStore.setHidden(context, location.id, hidden)
                            customLocations = CustomLocationStore.load(context)
                        },
                        onCustomLocationMove = { location, delta ->
                            CustomLocationStore.move(context, location.id, delta)
                            customLocations = CustomLocationStore.load(context)
                        },
                        onAddLocation = { addLocationLauncher.launch(null) },
                        onOpenApkExtractor = { navController.navigate(Screen.ApkExtractor.route) },
                        onOpenTextEditor = { runCatching { context.startActivity(Intent(context, MhTextEditorActivity::class.java)) } },
                        onOpenRecycleBin = { viewModel.openRecycleBin(activePane) },
                        onOpenKeyManager = {
                            appSettingsInitialPage = SettingsPage.SIGNATURE
                            showAppSettings = true
                        },
                        onRemoveBookmark = { path ->
                            val updated = bookmarks.filterNot { it == path }
                            bookmarks = updated
                            bookmarkPreferences.edit().putStringSet("paths", updated.toSet()).apply()
                        },
                        onOpenSettings = {
                            appSettingsInitialPage = SettingsPage.ROOT
                            showAppSettings = true
                        },
                        onClose = {
                            scope.launch { drawerState.close() }
                        }
                    )
                }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
            ) {
                // Header Bar
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isSearching) {
                        SearchBar(
                            query = activeState.searchQuery,
                            onQueryChange = { viewModel.setSearchQuery(activePane, it) },
                            onClose = {
                                isSearching = false
                                viewModel.clearSearch(activePane)
                            }
                        )
                    } else {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = { scope.launch { drawerState.open() } },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    Icons.Default.Menu,
                                    contentDescription = "Menu",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        goToPane = activePane
                                        showGoToPathDialog = true
                                    }
                            ) {
                                Text(
                                    text = activeState.displayPath,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                val diskRoot = if (!activeState.currentPath.startsWith("content://")) File(activeState.currentPath) else File(rootPath)
                                val totalDisk = diskRoot.totalSpace.takeIf { it > 0L } ?: File(rootPath).totalSpace
                                val freeDisk = diskRoot.freeSpace.takeIf { it >= 0L } ?: File(rootPath).freeSpace
                                val usedDisk = (totalDisk - freeDisk).coerceAtLeast(0L)
                                Text(
                                    text = buildString {
                                        append("Folders: ${activeState.folderCount} Files: ${activeState.fileCount}")
                                        if (activeState.selectedPaths.isNotEmpty()) append(" Selected: ${activeState.selectedPaths.size}")
                                        if (totalDisk > 0L) append(" Disk: ${formatDiskG(usedDisk)}/${formatDiskG(totalDisk)}")
                                    },
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }

                            IconButton(
                                onClick = { isSearching = true },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    Icons.Default.Search,
                                    contentDescription = "Search",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Box {
                                IconButton(
                                    onClick = { showApktoolOptions = true },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        Icons.Default.MoreVert,
                                        contentDescription = "More Options",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                DropdownMenu(
                                    expanded = showApktoolOptions,
                                    onDismissRequest = { showApktoolOptions = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Versteckte Dateien") },
                                        leadingIcon = { Icon(Icons.Default.Visibility, contentDescription = null) },
                                        onClick = { showApktoolOptions = false; showHiddenFiles = true },
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Sortieren") },
                                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = null) },
                                        onClick = { showApktoolOptions = false; showSortFiles = true },
                                    )
                                    DropdownMenuItem(
                                        text = { Text(if (activeState.filter == FileFilter.ALL) "Filter" else "Filter: ${fileFilterLabel(activeState.filter)}") },
                                        leadingIcon = { Icon(Icons.Default.FilterList, contentDescription = null) },
                                        onClick = { showApktoolOptions = false; showFilterFiles = true },
                                    )
                                    if (activeState.currentPath == viewModel.recycleBinPath()) {
                                        DropdownMenuItem(
                                            text = { Text("Papierkorb leeren") },
                                            onClick = { showApktoolOptions = false; viewModel.emptyRecycleBin(activePane) },
                                        )
                                    }
                                    DropdownMenuItem(
                                        text = { Text("Erstellen & Dekodieren") },
                                        onClick = { showApktoolOptions = false; showApktoolSettings = true }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Apktool Jobs (${apktoolJobs.count { !it.isTerminal }})") },
                                        onClick = { showApktoolOptions = false; showTaskPanel = true }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Apktool CLI") },
                                        onClick = { showApktoolOptions = false; showApktoolCli = true }
                                    )
                                }
                            }
                        }
                    }
                }

                // Dual Pane File List
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 2.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant)
                ) {
                    ClassicFilePane(
                        paneState = leftState,
                        isActive = activePane == ActivePane.LEFT,
                        onFocus = { viewModel.setActive(ActivePane.LEFT) },
                        onNavigateUp = { viewModel.navigateUp(ActivePane.LEFT) },
                        onRefresh = { viewModel.refreshDirectory(ActivePane.LEFT) },
                        onPathClick = { goToPane = ActivePane.LEFT; showGoToPathDialog = true },
                        onItemClick = { item ->
                            if (leftState.selectedPaths.isNotEmpty()) {
                                viewModel.toggleSelection(ActivePane.LEFT, item.path, false)
                            } else {
                                when {
                                    item.isSaf -> openSafItem(ActivePane.LEFT, item)

                                    isApkLike(File(item.path)) -> {
                                        val apk = File(item.path)
                                        apktoolTarget = apk
                                        targetItem = item
                                        if (apk.extension.equals("apk", ignoreCase = true)) { apkInfoPane = ActivePane.LEFT; showApkInfo = true }
                                        else { splitPackagePane = ActivePane.LEFT; showSplitPackage = true }
                                    }

                                    item.isArchiveFile() && ArchiveEngine.supports(File(item.path)) -> {
                                        viewModel.openArchive(ActivePane.LEFT, File(item.path))
                                    }

                                    item.isDirectory -> viewModel.loadDirectory(
                                        ActivePane.LEFT,
                                        item.path
                                    )

                                    item.isEditableTextFile() -> openInTextEditor(item)

                                    item.isImageFile() -> {
                                        navController.navigate(
                                            Screen.ImageViewer.createRoute(
                                                item.path,
                                                item.name
                                            )
                                        )
                                    }

                                    item.isAudioFile() || item.isVideoFile() -> {
                                        navController.navigate(Screen.MediaPlayer.createRoute(item.path, item.name, item.isVideoFile()))
                                    }

                                    else -> {
                                        FileOpener.openFile(navController.context, File(item.path))
                                    }
                                }
                            }
                        },
                        onItemLongClick = { item ->
                            targetItem = item
                            showContextMenu = true
                        },
                        onSwipeSelect = { item ->
                            viewModel.toggleSelection(
                                ActivePane.LEFT,
                                item.path,
                                true
                            )
                        },
                        onBuildProject = { project ->
                            apktoolTarget = project
                            showApktoolBuild = true
                        },
                        modifier = Modifier.weight(1f)
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(1.dp)
                            .background(MaterialTheme.colorScheme.outlineVariant)
                    )

                    ClassicFilePane(
                        paneState = rightState,
                        isActive = activePane == ActivePane.RIGHT,
                        onFocus = { viewModel.setActive(ActivePane.RIGHT) },
                        onNavigateUp = { viewModel.navigateUp(ActivePane.RIGHT) },
                        onRefresh = { viewModel.refreshDirectory(ActivePane.RIGHT) },
                        onPathClick = { goToPane = ActivePane.RIGHT; showGoToPathDialog = true },
                        onItemClick = { item ->
                            if (rightState.selectedPaths.isNotEmpty()) {
                                viewModel.toggleSelection(ActivePane.RIGHT, item.path, false)
                            } else {
                                when {
                                    item.isSaf -> openSafItem(ActivePane.RIGHT, item)

                                    isApkLike(File(item.path)) -> {
                                        val apk = File(item.path)
                                        apktoolTarget = apk
                                        targetItem = item
                                        if (apk.extension.equals("apk", ignoreCase = true)) { apkInfoPane = ActivePane.RIGHT; showApkInfo = true }
                                        else { splitPackagePane = ActivePane.RIGHT; showSplitPackage = true }
                                    }

                                    item.isArchiveFile() && ArchiveEngine.supports(File(item.path)) -> {
                                        viewModel.openArchive(ActivePane.RIGHT, File(item.path))
                                    }

                                    item.isDirectory -> viewModel.loadDirectory(
                                        ActivePane.RIGHT,
                                        item.path
                                    )

                                    item.isEditableTextFile() -> openInTextEditor(item)

                                    item.isImageFile() -> {
                                        navController.navigate(
                                            Screen.ImageViewer.createRoute(
                                                item.path,
                                                item.name
                                            )
                                        )
                                    }

                                    item.isAudioFile() || item.isVideoFile() -> {
                                        navController.navigate(Screen.MediaPlayer.createRoute(item.path, item.name, item.isVideoFile()))
                                    }

                                    else -> {
                                        FileOpener.openFile(navController.context, File(item.path))
                                    }
                                }
                            }
                        },
                        onItemLongClick = { item ->
                            targetItem = item
                            showContextMenu = true
                        },
                        onSwipeSelect = { item ->
                            viewModel.toggleSelection(
                                ActivePane.RIGHT,
                                item.path,
                                true
                            )
                        },
                        onBuildProject = { project ->
                            apktoolTarget = project
                            showApktoolBuild = true
                        },
                        modifier = Modifier.weight(1f)
                    )
                }

                // Bottom Navigation Bar
                if (hasSelectedItems) {
                    SelectionBottomBar(
                        selectedCount = activeState.selectedPaths.size,
                        onSelectAll = { viewModel.selectAll(activePane) },
                        onCopySelected = { viewModel.copySelectedToOppositePane(activePane) },
                        onMoveSelected = { viewModel.moveSelectedToOppositePane(activePane) },
                        onDeleteSelected = { requestDelete(activePane) },
                        onMoreOptions = { showSelectionMore = true },
                        modifier = Modifier
                            .bookmarkSwipeUp { showBookmarkSheet = true }
                            .drawerSwipe(
                                onOpenLeft = { scope.launch { drawerState.open() } },
                                onOpenRight = { showTaskPanel = true },
                            ),
                    )
                } else {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        shadowElevation = 2.dp,
                        modifier = Modifier.fillMaxWidth()
                            .bookmarkSwipeUp { showBookmarkSheet = true }
                            .drawerSwipe(
                                onOpenLeft = { scope.launch { drawerState.open() } },
                                onOpenRight = { showTaskPanel = true },
                            )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(io.github.lootdev78.mtapktool.core.theme.MtClassicMetrics.bottomBarHeight),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            BottomNavIconButton(
                                icon = Icons.AutoMirrored.Filled.ArrowBack,
                                onClick = { viewModel.navigateHistoryBack(activePane) }
                            )
                            BottomNavIconButton(
                                icon = Icons.AutoMirrored.Filled.ArrowForward,
                                onClick = { viewModel.navigateHistoryForward(activePane) }
                            )
                            BottomNavIconButton(
                                icon = Icons.Default.Add,
                                onClick = { showCreateDialog = true }
                            )
                            BottomNavIconButton(
                                icon = Icons.Default.SwapHoriz,
                                onClick = { viewModel.swapPanes() }
                            )
                            BottomNavIconButton(
                                icon = Icons.Default.ArrowUpward,
                                onClick = { goToPane = activePane; showGoToPathDialog = true }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showBookmarkSheet) {
        BookmarkBottomSheet(
            bookmarks = bookmarks,
            currentPath = activeState.currentPath,
            activePane = activePane,
            onDismiss = { showBookmarkSheet = false },
            onOpen = { pane, path ->
                showBookmarkSheet = false
                viewModel.setActive(pane)
                viewModel.navigateToDirectPath(pane, path)
            },
            onAddCurrent = {
                val path = activeState.currentPath
                if (path.isNotBlank()) {
                    val updated = (bookmarks + path).distinct().sorted()
                    bookmarks = updated
                    bookmarkPreferences.edit().putStringSet("paths", updated.toSet()).apply()
                }
            },
            onRemove = { path ->
                val updated = bookmarks.filterNot { it == path }
                bookmarks = updated
                bookmarkPreferences.edit().putStringSet("paths", updated.toSet()).apply()
            },
        )
    }

    if (showSelectionMore) {
        MtClassicAlertDialog(
            onDismissRequest = { showSelectionMore = false },
            title = { Text("${activeState.selectedPaths.size} ausgewählt") },
            text = {
                Column {
                    TextButton(onClick = { showSelectionMore = false; viewModel.invertSelection(activePane) }) { Text("AUSWAHL UMKEHREN") }
                    TextButton(onClick = {
                        showSelectionMore = false
                        archivePane = activePane
                        archiveSources = activeState.selectedPaths.map(::File)
                        showArchiveDialog = archiveSources.isNotEmpty()
                    }) { Text("KOMPRIMIEREN") }
                    TextButton(onClick = { showSelectionMore = false; viewModel.cancelSelection(activePane) }) { Text("AUSWAHL BEENDEN") }
                }
            },
            confirmButton = { TextButton(onClick = { showSelectionMore = false }) { Text("SCHLIESSEN") } },
        )
    }

    ApktoolTaskPanel(
        visible = showTaskPanel,
        jobs = apktoolJobs,
        archiveTasks = archiveTasks,
        onOpenJob = { jobId ->
            showTaskPanel = false
            selectedJobId = jobId
        },
        onCancel = apktoolJobsViewModel::cancel,
        onCancelAll = apktoolJobsViewModel::cancelAll,
        onCancelArchive = viewModel::cancelArchiveTask,
        onCancelAllArchive = viewModel::cancelAllArchiveTasks,
        onRemove = apktoolJobsViewModel::dismiss,
        onClearFinished = apktoolJobsViewModel::clearFinished,
        onDismiss = { showTaskPanel = false },
    )
}

private fun formatDiskG(bytes: Long): String = String.format(Locale.US, "%.2fG", bytes.toDouble() / (1024.0 * 1024.0 * 1024.0))

private fun Modifier.bookmarkSwipeUp(onOpen: () -> Unit): Modifier = pointerInput(onOpen) {
    val threshold = 44.dp.toPx()
    var totalY = 0f
    var opened = false
    detectVerticalDragGestures(
        onDragStart = { totalY = 0f; opened = false },
        onVerticalDrag = { change, dragAmount ->
            totalY += dragAmount
            if (!opened && totalY <= -threshold) {
                opened = true
                onOpen()
                change.consume()
            }
        },
    )
}

private fun Modifier.drawerSwipe(
    onOpenLeft: () -> Unit,
    onOpenRight: () -> Unit,
): Modifier = pointerInput(onOpenLeft, onOpenRight) {
    var startX = 0f
    var total = 0f
    var opened = false
    detectHorizontalDragGestures(
        onDragStart = { offset ->
            startX = offset.x
            total = 0f
            opened = false
        },
        onHorizontalDrag = { change, amount ->
            total += amount
            val fromLeft = startX <= size.width * 0.28f
            val fromRight = startX >= size.width * 0.72f
            if (!opened && fromLeft && total >= 72f) {
                opened = true
                onOpenLeft()
                change.consume()
            } else if (!opened && fromRight && total <= -72f) {
                opened = true
                onOpenRight()
                change.consume()
            }
        },
        onDragEnd = { total = 0f; opened = false },
        onDragCancel = { total = 0f; opened = false },
    )
}

@Composable
fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.Search, contentDescription = null)
        Spacer(Modifier.width(12.dp))
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.weight(1f),
            textStyle = LocalTextStyle.current.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.onSurfaceVariant),
            decorationBox = { innerTextField ->
                if (query.isEmpty()) {
                    Text("Search files...", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                }
                innerTextField()
            }
        )
        IconButton(onClick = onClose) {
            Icon(Icons.Default.Close, contentDescription = "Close Search")
        }
    }
}

@Composable
private fun BottomNavIconButton(
    icon: ImageVector,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(
                    bounded = true,
                    radius = 24.dp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp)
        )
    }
}
private fun installApk(context: Context, file: File) {
    fun launchInstaller() {
        runCatching {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(intent)
        }.onFailure { error ->
            Toast.makeText(context, "Installieren fehlgeschlagen: ${error.message}", Toast.LENGTH_SHORT).show()
        }
    }

    if (!ExplorerPreferences.current(context).apkInstallationVerification) {
        launchInstaller()
        return
    }

    CoroutineScope(Dispatchers.IO).launch {
        val problem = runCatching {
            val verifyResult = ApkVerifier.Builder(file).build().verify()
            if (!verifyResult.isVerified) return@runCatching "APK-Signaturprüfung fehlgeschlagen."

            val pm = context.packageManager
            val archive = pm.packageArchiveInfoCompat(file.absolutePath)
            if (archive != null) {
                val installed = pm.packageInfoCompat(archive.packageName)
                if (installed != null && archive.longVersionCode < installed.longVersionCode) {
                    return@runCatching "Versionscode ${archive.longVersionCode} ist niedriger als die installierte Version ${installed.longVersionCode}."
                }
            }
            null
        }.getOrElse { "APK-Prüfung fehlgeschlagen: ${it.message ?: it.javaClass.simpleName}" }

        withContext(Dispatchers.Main) {
            if (problem == null) launchInstaller()
            else Toast.makeText(context, problem, Toast.LENGTH_LONG).show()
        }
    }
}

private fun shareFile(context: Context, file: File) {
    runCatching {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension.lowercase()) ?: "*/*"
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri(file.name, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Datei teilen"))
    }.onFailure { error ->
        Toast.makeText(context, "Teilen fehlgeschlagen: ${error.message}", Toast.LENGTH_SHORT).show()
    }
}


private fun PackageManager.packageArchiveInfoCompat(path: String): PackageInfo? = runCatching {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getPackageArchiveInfo(path, PackageManager.PackageInfoFlags.of(0))
    } else {
        javaClass.getMethod("getPackageArchiveInfo", String::class.java, Int::class.javaPrimitiveType)
            .invoke(this, path, 0) as? PackageInfo
    }
}.getOrNull()

private fun PackageManager.packageInfoCompat(packageName: String): PackageInfo? = runCatching {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
    } else {
        javaClass.getMethod("getPackageInfo", String::class.java, Int::class.javaPrimitiveType)
            .invoke(this, packageName, 0) as? PackageInfo
    }
}.getOrNull()
