package io.github.lootdev78.mtapktool.feature.explorer.screen

import io.github.lootdev78.mtapktool.feature.ftp.*
import io.github.lootdev78.mtapktool.feature.tools.ToolsActivity
import androidx.compose.material3.LinearProgressIndicator
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
import io.github.lootdev78.mtapktool.feature.explorer.component.FilePermissionsDialog
import io.github.lootdev78.mtapktool.feature.explorer.component.AdvancedSearchDialog
import io.github.lootdev78.mtapktool.feature.explorer.component.MultiRenameDialog
import io.github.lootdev78.mtapktool.tasks.ToolTaskRegistry
import io.github.lootdev78.mtapktool.ExplorerOutputBridge
import io.github.lootdev78.mtapktool.apktool.ApkFunctionsDialog
import io.github.lootdev78.mtapktool.apktool.ApkEditorAction
import io.github.lootdev78.mtapktool.apktool.ApkEditorDialog
import io.github.lootdev78.mtapktool.apktool.ApkInfoDialog
import io.github.lootdev78.mtapktool.apktool.ApkCloneDialog
import io.github.lootdev78.mtapktool.apktool.ApktoolBuildDialog
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
import io.github.lootdev78.mtapktool.archive.ArchiveCharsetDialog
import io.github.lootdev78.mtapktool.archive.ArchiveTestResultDialog
import io.github.lootdev78.mtapktool.archive.ArchiveCharsets
import io.github.lootdev78.mtapktool.archive.ArchiveFormat
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
import io.github.lootdev78.mtapktool.feature.explorer.component.BookmarkEditorDialog
import io.github.lootdev78.mtapktool.feature.explorer.component.BookmarkDeleteDialog
import io.github.lootdev78.mtapktool.feature.explorer.state.Bookmark
import io.github.lootdev78.mtapktool.feature.explorer.state.BookmarkStore
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
import io.github.lootdev78.mtapktool.settings.SettingsActivity
import io.github.lootdev78.mtapktool.settings.SettingsPage
import io.github.lootdev78.mtapktool.settings.ExplorerPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import modder.hub.editor.MainActivity as MhTextEditorActivity
import androidx.compose.ui.res.painterResource
import io.github.lootdev78.mtapktool.R
import androidx.compose.material3.HorizontalDivider

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
    val toolTasks by ToolTaskRegistry.tasks.collectAsState()
    var selectedToolTaskId by remember { mutableStateOf<String?>(null) }
    var permissionsItem by remember { mutableStateOf<FileItem?>(null) }
    val fileTransfer by viewModel.fileTransfer.collectAsState()
    val remoteEditRequest by viewModel.remoteEditRequest.collectAsState()
    var showFtpClient by remember { mutableStateOf(false) }
    var showFtpServer by remember { mutableStateOf(false) }
    var transferRequest by remember { mutableStateOf<Pair<ActivePane, Boolean>?>(null) }
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
    val archiveTestReport by viewModel.archiveTestReport.collectAsState()
    var archiveCharsetPane by remember { mutableStateOf<ActivePane?>(null) }

    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshMountedArchiveStates()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    var bookmarks by remember(context) { mutableStateOf(BookmarkStore.load(context)) }
    var bookmarkEditor by remember { mutableStateOf<Bookmark?>(null) }
    var bookmarkEditingPath by remember { mutableStateOf<String?>(null) }
    var bookmarkToDelete by remember { mutableStateOf<Bookmark?>(null) }
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
    var showAdvancedSearch by remember { mutableStateOf(false) }
    var searchPane by remember { mutableStateOf(ActivePane.LEFT) }
    var renamePane by remember { mutableStateOf(ActivePane.LEFT) }
    var renameItems by remember { mutableStateOf<List<FileItem>>(emptyList()) }
    var showMultiRename by remember { mutableStateOf(false) }
    var keyImportPath by remember { mutableStateOf<String?>(null) }
    var searchHistory by remember { mutableStateOf(viewModel.searchHistory()) }
    var targetItem by remember { mutableStateOf<FileItem?>(null) }
    var isSearching by remember { mutableStateOf(false) }
    var showApkInfo by remember { mutableStateOf(false) }
    var apkInfoPane by remember { mutableStateOf(ActivePane.LEFT) }
    var showSplitPackage by remember { mutableStateOf(false) }
    var splitPackagePane by remember { mutableStateOf(ActivePane.LEFT) }
    var showApkFunctions by remember { mutableStateOf(false) }
    var apkEditorAction by remember { mutableStateOf<ApkEditorAction?>(null) }
    var showApkClone by remember { mutableStateOf(false) }
    var showApktoolDecode by remember { mutableStateOf(false) }
    var showFrameworkImport by remember { mutableStateOf(false) }
    var showApktoolBuild by remember { mutableStateOf(false) }
    var showApktoolSettings by remember { mutableStateOf(false) }
    var showAppSettings by remember { mutableStateOf(false) }
    var appSettingsInitialPage by remember { mutableStateOf(SettingsPage.ROOT) }
    var showTaskPanel by remember { mutableStateOf(false) }
    var selectedJobId by remember { mutableStateOf<String?>(null) }
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

    val hasSelectedItems = leftState.selectedPaths.isNotEmpty() || rightState.selectedPaths.isNotEmpty()
    val selectionPane = if (activeState.selectedPaths.isNotEmpty()) activePane else if (activePane == ActivePane.LEFT) ActivePane.RIGHT else ActivePane.LEFT
    val selectionState = if (selectionPane == ActivePane.LEFT) leftState else rightState
    fun persistBookmarks(entries: List<Bookmark>) {
        bookmarks = entries
        BookmarkStore.save(context, entries)
    }
    fun requestAddBookmark(path: String, name: String = BookmarkStore.defaultName(path)) {
        if (activeState.isArchiveView || path.startsWith("mtftp://")) {
            Toast.makeText(context, "Archiv-Arbeitsordner sind temporär; FTP-Orte über das Verbindungsprofil speichern", Toast.LENGTH_LONG).show()
            return
        }
        val existing = bookmarks.firstOrNull { it.path == path }
        bookmarkEditingPath = existing?.path
        bookmarkEditor = existing ?: Bookmark(path, name)
    }
    val canNavigateBack = viewModel.canNavigateUp(activePane)

    fun openInTextEditor(item: FileItem) {
        if (item.isFtp) {
            scope.launch {
                val result = runCatching { viewModel.materializeFtpItem(item) }
                result.onSuccess { file ->
                    context.startActivity(Intent(context, MhTextEditorActivity::class.java).putExtra("path", file.absolutePath).putExtra("name", item.name))
                }.onFailure { Toast.makeText(context, it.message, Toast.LENGTH_LONG).show() }
            }
            return
        }
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
        if (item.isDirectory && (item.isFtp || item.isSaf)) {
            scope.launch {
                viewModel.setPaneBusy(pane, true, "Bereite ${item.name} vor")
                val result = runCatching { viewModel.materializeForArchive(item) }
                viewModel.setPaneBusy(pane, false)
                result.onSuccess(onReady).onFailure { Toast.makeText(context, it.message, Toast.LENGTH_LONG).show() }
            }
            return
        }
        if (item.isFtp) {
            scope.launch {
                viewModel.setPaneBusy(pane, true, "Lade ${item.name}")
                val result = runCatching { viewModel.materializeFtpItem(item) }
                viewModel.setPaneBusy(pane, false)
                result.onSuccess(onReady).onFailure { Toast.makeText(context, it.message, Toast.LENGTH_LONG).show() }
            }
            return
        }
        if (!item.isSaf) {
            onReady(File(item.path))
            return
        }
        scope.launch {
            viewModel.setPaneBusy(pane, true, "Bereite ${item.name} vor")
            val result = runCatching { viewModel.materializeDocumentItem(item, pane) }
            viewModel.setPaneBusy(pane, false)
            result.onSuccess(onReady)
                .onFailure { Toast.makeText(context, it.message ?: "Datei konnte nicht geöffnet werden", Toast.LENGTH_SHORT).show() }
        }
    }

    fun revealIncomingInLastPane(item: FileItem) {
        if (item.isFtp) { viewModel.highlightDocument(activePane, FtpLocation.parent(item.path), item.name); return }
        if (!item.isSaf) {
            viewModel.revealOutput(activePane, item.path)
            return
        }
        val pane = activePane
        scope.launch {
            val location = withContext(Dispatchers.IO) { runCatching { io.github.lootdev78.mtapktool.feature.explorer.util.ExternalUriLocator.locate(context, Uri.parse(item.path)) }.getOrNull() }
            when {
                location?.localFile != null -> viewModel.revealOutput(pane, location.localFile.absolutePath)
                location?.parentDocumentUri != null -> viewModel.highlightDocument(pane, location.parentDocumentUri, item.name)
                else -> Toast.makeText(context, "Der Dokumentanbieter gibt den Originalordner nicht frei. Über Speicher hinzufügen kann Ordnerzugriff erlaubt werden.", Toast.LENGTH_LONG).show()
            }
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

    fun openRemoteItem(pane: ActivePane, item: FileItem) {
        if (item.isDirectory) { viewModel.loadDirectory(pane, item.path); return }
        if (item.isEditableTextFile()) { openInTextEditor(item); return }
        materializeForTool(item, pane) { local ->
            when {
                item.isArchiveFile() && ArchiveEngine.supports(local) -> viewModel.openArchive(pane, local)
                isApkLike(local) -> { apktoolTarget = local; targetItem = FileItem(local); apkInfoPane = pane; showApkInfo = true }
                item.isImageFile() -> navController.navigate(Screen.ImageViewer.createRoute(local.absolutePath, item.name))
                item.isAudioFile() || item.isVideoFile() -> navController.navigate(Screen.MediaPlayer.createRoute(local.absolutePath, item.name, item.isVideoFile()))
                else -> FileOpener.openFile(context, local)
            }
        }
    }

    if (showFtpClient) FtpClientDialog(activePane, { showFtpClient = false }, viewModel::connectFtp)
    if (showFtpServer) FtpServerDialog(if (activeState.isFtpView || viewModel.isSafPane(activePane) || activeState.isArchiveView) rootPath else activeState.currentPath) { showFtpServer = false }
    transferRequest?.let { (sourcePane, move) ->
        val source = if (sourcePane == ActivePane.LEFT) leftState else rightState
        val target = if (sourcePane == ActivePane.LEFT) rightState else leftState
        MtClassicAlertDialog(onDismissRequest = { transferRequest = null }, title = { Text(if (move) "Auswahl verschieben" else "Auswahl kopieren") }, text = {
            Column {
                Text("${source.selectedPaths.size} Element(e)")
                Text("Quelle: ${source.displayPath}", style = MaterialTheme.typography.bodySmall)
                Text("Ziel: ${target.displayPath}", style = MaterialTheme.typography.bodySmall)
            }
        }, dismissButton = { TextButton(onClick = { transferRequest = null }) { Text("ABBRECHEN") } }, confirmButton = {
            TextButton(enabled = !fileTransfer.running, onClick = {
                transferRequest = null
                if (move) viewModel.moveSelectedToOppositePane(sourcePane) else viewModel.copySelectedToOppositePane(sourcePane)
            }) { Text(if (move) "VERSCHIEBEN" else "KOPIEREN") }
        })
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
                    transferRequest = activePane to false
                }
                showContextMenu = false
            },
            onMove = {
                targetItem?.let { item ->
                    viewModel.ensureSelected(activePane, item.path)
                    transferRequest = activePane to true
                }
                showContextMenu = false
            },
            onRename = {
                val selected = activeState.filteredItems.filter { it.path in activeState.selectedPaths }
                if (selected.size > 1 && targetItem?.path in activeState.selectedPaths) {
                    renamePane = activePane; renameItems = selected; showMultiRename = true
                } else showRenameDialog = true
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
                    materializeForTool(item, activePane) { file -> runCatching {
                        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                        val mimeType = item.mimeType ?: MimeTypeMap.getSingleton().getMimeTypeFromExtension(item.extensionName) ?: "*/*"
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = mimeType
                            putExtra(Intent.EXTRA_STREAM, uri)
                            clipData = ClipData.newRawUri(item.name, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(intent, "Datei teilen"))
                    }.onFailure { error -> Toast.makeText(context, "Teilen fehlgeschlagen: ${error.message}", Toast.LENGTH_SHORT).show() } }
                }
                showContextMenu = false
            },
            onOpenWith = {
                showContextMenu = false
                showBuiltInOpen = true
            },
            onAddBookmark = {
                targetItem?.let { item ->
                    requestAddBookmark(item.path, item.name)
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
            onImageViewer = {
                showBuiltInOpen = false
                materializeForTool(item, activePane) { file -> navController.navigate(Screen.ImageViewer.createRoute(file.absolutePath, item.name)) }
            },
            onMediaPlayer = {
                showBuiltInOpen = false
                materializeForTool(item, activePane) { file -> navController.navigate(Screen.MediaPlayer.createRoute(file.absolutePath, item.name, item.isVideoFile())) }
            },
            onKeyImport = {
                showBuiltInOpen = false
                materializeForTool(item, activePane) { file ->
                    scope.launch {
                        val result = runCatching { ToolTaskRegistry.run("Keystore importieren", item.name) {
                            withContext(Dispatchers.IO) {
                                if (file.length() > 16 * 1024 * 1024) throw java.io.IOException("Keystore größer als 16 MiB")
                                val directory = File(context.filesDir, "signing-keys").apply { mkdirs() }
                                val output = File(directory, "import-${java.util.UUID.randomUUID()}.${file.extension.ifEmpty { "keystore" }}")
                                try { file.copyTo(output); output.absolutePath } catch (error: Exception) { output.delete(); throw error }
                            }
                        } }
                        result.onSuccess { keyImportPath = it }.onFailure { Toast.makeText(context, it.message, Toast.LENGTH_LONG).show() }
                    }
                }
            },
            onExternalApp = { mime ->
                showBuiltInOpen = false
                if (item.isSaf) FileOpener.openUri(context, Uri.parse(item.path), item.name, mime)
                else materializeForTool(item, activePane) { file -> FileOpener.openFile(context, file, mime) }
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
        if (item.isSaf || item.isFtp) {
            MtClassicAlertDialog(
                onDismissRequest = { showPropertyDialog = false },
                title = { Text(item.name) },
                text = {
                    Column {
                        Text(if (item.isDirectory) "Folder" else "File")
                        if (!item.isDirectory) Text("Size: ${item.sizeText}")
                        Text(if (item.isFtp) "Storage: FTP" else "Storage: Android document tree", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        TextButton(onClick = { showPropertyDialog = false; permissionsItem = item }) { Text("BERECHTIGUNGEN") }
                    }
                },
                confirmButton = { TextButton(onClick = { showPropertyDialog = false }) { Text("OK") } },
            )
        } else {
            FileInfoDialog(
                file = File(item.path),
                onPermissions = { showPropertyDialog = false; permissionsItem = item },
                onDismiss = { showPropertyDialog = false }
            )
        }
    }
    permissionsItem?.let { item ->
        FilePermissionsDialog(item,
            onDismiss = { permissionsItem = null },
            onChanged = { viewModel.refreshMountedArchiveStates() },
            onDocumentGrant = { permissionsItem = null; addLocationLauncher.launch(null) },
            readRemote = { viewModel.ftpPermissions(item.path) },
            writeRemote = { mode -> viewModel.setFtpPermissions(item.path, mode) },
        )
    }

    remoteEditRequest?.let { edit ->
        MtClassicAlertDialog(
            onDismissRequest = { viewModel.resolveRemoteEdit(false) },
            title = { Text(if (edit.item.isSaf) "Dokument geändert" else "FTP-Datei geändert") },
            text = { Text("${edit.item.name}\nDie lokale Arbeitskopie wurde geändert. " + (if (edit.item.isSaf) "Zum Originaldokument zurückschreiben?" else "Auf den FTP-Server hochladen?") + "\n\nLokale Kopie: ${edit.file.absolutePath}") },
            confirmButton = { TextButton(onClick = { viewModel.resolveRemoteEdit(true) }) { Text(if (edit.item.isSaf) "ZURÜCKSCHREIBEN" else "HOCHLADEN") } },
            dismissButton = { TextButton(onClick = { viewModel.resolveRemoteEdit(false) }) { Text("LOKAL BEHALTEN") } },
        )
    }

    if (keyImportPath != null) io.github.lootdev78.mtapktool.feature.keys.KeyManagerDialog(onBack = { keyImportPath = null }, initialPath = keyImportPath)
    if (showAdvancedSearch) {
        AdvancedSearchDialog(
            directory = (if (searchPane == ActivePane.LEFT) leftState else rightState).displayPath,
            history = searchHistory,
            onClearHistory = { viewModel.clearSearchHistory(); searchHistory = emptyList() },
            onSearch = { viewModel.advancedSearch(searchPane, it); searchHistory = viewModel.searchHistory() },
            onDismiss = { showAdvancedSearch = false },
        )
    }
    if (showMultiRename) {
        MultiRenameDialog(
            count = renameItems.size,
            onPreview = { viewModel.previewRename(renamePane, renameItems, it) },
            onRename = viewModel::renameMultiple,
            onDismiss = { showMultiRename = false },
        )
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
                onEditorAction = { action -> showApkFunctions = false; apkEditorAction = action },
            )
        } ?: run { showApkFunctions = false }
    }

    apkEditorAction?.let { action ->
        apktoolTarget?.takeIf { it.isFile && it.extension.equals("apk", true) }?.let { file ->
            ApkEditorDialog(file, action, onDismiss = { apkEditorAction = null }, onJobQueued = { id ->
                jobPaneById = jobPaneById + (id to apkInfoPane); selectedJobId = id
            })
        }
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

    LaunchedEffect(showAppSettings) {
        if (showAppSettings) {
            context.startActivity(SettingsActivity.intent(context, appSettingsInitialPage))
            showAppSettings = false
        }
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

    archiveTestReport?.let { report ->
        ArchiveTestResultDialog(report, onDismiss = viewModel::dismissArchiveTestReport)
    }
    archiveCharsetPane?.let { pane ->
        val state = if (pane == ActivePane.LEFT) leftState else rightState
        val path = state.archiveFilePath
        if (path != null) ArchiveCharsetDialog(File(path).name, state.archiveCharset,
            onDismiss = { archiveCharsetPane = null },
            onConfirm = { charset -> archiveCharsetPane = null; viewModel.reloadArchiveCharset(pane, charset) })
        else LaunchedEffect(pane) { archiveCharsetPane = null }
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
                        onBookmarkClick = { pane, path -> viewModel.setActive(pane); viewModel.navigateToDirectPath(pane, path) },
                        onEditBookmark = { bookmark -> bookmarkEditingPath = bookmark.path; bookmarkEditor = bookmark },
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
                        onOpenTasks = { showTaskPanel = true },
                        onOpenFtpClient = { showFtpClient = true },
                        onOpenFtpServer = { showFtpServer = true },
                        onOpenInspector = { context.startActivity(ToolsActivity.intent(context, "inspector")) },
                        onOpenColorPicker = { context.startActivity(ToolsActivity.intent(context, "color")) },
                        onDisconnectFtp = { viewModel.disconnectFtp(activePane) },
                        onOpenTextEditor = { runCatching { context.startActivity(Intent(context, MhTextEditorActivity::class.java)) } },
                        onOpenRecycleBin = { viewModel.openRecycleBin(activePane) },
                        onOpenKeyManager = {
                            appSettingsInitialPage = SettingsPage.SIGNATURE
                            showAppSettings = true
                        },
                        onRemoveBookmark = { bookmark -> bookmarkToDelete = bookmark },
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
                                onClick = { searchPane = activePane; searchHistory = viewModel.searchHistory(); showAdvancedSearch = true },
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
                                        text = { Text("Aktualisieren") },
                                        leadingIcon = { Icon(painterResource(R.drawable.mt_ic_refresh), null) },
                                        onClick = { showApktoolOptions = false; viewModel.refreshDirectory(activePane) },
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Filter") },
                                        leadingIcon = { Icon(painterResource(R.drawable.mt_ic_filter), null) },
                                        onClick = { showApktoolOptions = false; isSearching = !isSearching; if (!isSearching) viewModel.clearSearch(activePane) },
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Suchen") },
                                        leadingIcon = { Icon(painterResource(R.drawable.mt_ic_search), null) },
                                        onClick = { showApktoolOptions = false; searchPane = activePane; searchHistory = viewModel.searchHistory(); showAdvancedSearch = true },
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Alles auswählen") },
                                        leadingIcon = { Icon(painterResource(R.drawable.mt_ic_select_all), null) },
                                        onClick = { showApktoolOptions = false; viewModel.selectAll(activePane) },
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Sortieren") },
                                        leadingIcon = { Icon(painterResource(R.drawable.mt_ic_sort), null) },
                                        onClick = { showApktoolOptions = false; showSortFiles = true },
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Versteckte Dateien  ›") },
                                        leadingIcon = { Icon(painterResource(R.drawable.mt_ic_hidden), null) },
                                        onClick = { showApktoolOptions = false; showHiddenFiles = true },
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Zu Lesezeichen hinzufügen") },
                                        enabled = !activeState.isArchiveView && !activeState.isFtpView,
                                        leadingIcon = { Icon(painterResource(R.drawable.mt_ic_bookmark), null) },
                                        onClick = {
                                            showApktoolOptions = false
                                            requestAddBookmark(activeState.currentPath)
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Als Home festlegen") },
                                        enabled = !activeState.isArchiveView && !activeState.isFtpView,
                                        leadingIcon = { Icon(painterResource(R.drawable.mt_ic_home), null) },
                                        onClick = { showApktoolOptions = false; viewModel.setAsHome(activePane) },
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Panels tauschen") },
                                        leadingIcon = { Icon(painterResource(R.drawable.mt_ic_swap), null) },
                                        onClick = { showApktoolOptions = false; viewModel.swapPanes() },
                                    )
                                    if (activeState.isArchiveView) {
                                        HorizontalDivider()
                                        DropdownMenuItem(
                                            text = { Text("Archiv testen") },
                                            leadingIcon = { Icon(painterResource(R.drawable.mt_ic_selection_info), null) },
                                            enabled = !activeState.isLoading,
                                            onClick = { showApktoolOptions = false; viewModel.testOpenArchive(activePane) },
                                        )
                                        val format = activeState.archiveFilePath?.let { ArchiveFormat.fromFile(File(it)) }
                                        val canChooseCharset = ArchiveCharsets.supports(format)
                                        DropdownMenuItem(
                                            text = { Text(if (canChooseCharset) "Zeichensatz…" else "Zeichensatz (vom Format festgelegt)") },
                                            leadingIcon = { Icon(painterResource(R.drawable.mt_ic_text_snippet), null) },
                                            enabled = canChooseCharset && !activeState.isLoading,
                                            onClick = { showApktoolOptions = false; archiveCharsetPane = activePane },
                                        )
                                        HorizontalDivider()
                                    }
                                    DropdownMenuItem(
                                        text = { Text("Einstellungen") },
                                        leadingIcon = { Icon(painterResource(R.drawable.mt_ic_settings), null) },
                                        onClick = { showApktoolOptions = false; appSettingsInitialPage = SettingsPage.ROOT; showAppSettings = true },
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Beenden") },
                                        leadingIcon = { Icon(painterResource(R.drawable.mt_ic_exit), null) },
                                        onClick = { showApktoolOptions = false; viewModel.requestExit { (context as? android.app.Activity)?.finishAffinity() } },
                                    )
                                }
                            }
                        }
                    }
                }

                if (leftState.searchResultsLabel != null || rightState.searchResultsLabel != null) {
                    Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant)) {
                        ActivePane.entries.forEach { pane ->
                            val resultState = if (pane == ActivePane.LEFT) leftState else rightState
                            Row(Modifier.weight(1f).padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                resultState.searchResultsLabel?.let { label ->
                                    Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                                    IconButton(onClick = { viewModel.clearAdvancedSearch(pane) }, modifier = Modifier.size(30.dp)) {
                                        Icon(painterResource(R.drawable.mt_ic_close), contentDescription = "Suchergebnisse schließen")
                                    }
                                }
                            }
                        }
                    }
                }
                // Dual Pane File List
                if (leftState.isArchiveView || rightState.isArchiveView) {
                    Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant)) {
                        ActivePane.entries.forEach { pane ->
                            val state = if (pane == ActivePane.LEFT) leftState else rightState
                            Column(Modifier.weight(1f).padding(horizontal = 6.dp, vertical = 2.dp)) {
                                if (state.isArchiveView) {
                                    val status = when (state.archiveStatus) {
                                        io.github.lootdev78.mtapktool.archive.ArchiveSessionState.DIRTY -> "Geändert: ${state.archiveChanges}"
                                        io.github.lootdev78.mtapktool.archive.ArchiveSessionState.UPDATING -> "Wird aktualisiert …"
                                        io.github.lootdev78.mtapktool.archive.ArchiveSessionState.FAILED -> "Aktualisierung fehlgeschlagen"
                                        else -> "Archiv geöffnet"
                                    }
                                    Text(status, fontSize = 11.sp, color = if (state.archiveChanges > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                                    if (state.archiveChanges > 0 || state.archiveStatus == io.github.lootdev78.mtapktool.archive.ArchiveSessionState.FAILED) TextButton(onClick = { viewModel.updateArchive(pane) }, enabled = !state.isLoading) { Text("AKTUALISIEREN", fontSize = 10.sp) }
                                }
                            }
                        }
                    }
                }
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
                                    item.isFtp -> openRemoteItem(ActivePane.LEFT, item)

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
                                    item.isFtp -> openRemoteItem(ActivePane.RIGHT, item)

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

                if (fileTransfer.running) {
                    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant).padding(horizontal = 10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("${fileTransfer.label} • ${fileTransfer.completed}/${fileTransfer.total} • ${io.github.lootdev78.mtapktool.feature.explorer.model.formatSize(fileTransfer.bytes)}", Modifier.weight(1f), fontSize = 11.sp, maxLines = 1)
                            TextButton(onClick = viewModel::cancelFileTransfer) { Text("ABBRECHEN") }
                        }
                        LinearProgressIndicator(progress = { if (fileTransfer.total == 0) 0f else fileTransfer.completed.toFloat() / fileTransfer.total }, modifier = Modifier.fillMaxWidth())
                    }
                }
                // Bottom Navigation Bar
                if (hasSelectedItems) {
                    SelectionBottomBar(
                        selectedCount = selectionState.selectedPaths.size,
                        onSelectAll = { ActivePane.entries.filter { (if (it == ActivePane.LEFT) leftState else rightState).selectedPaths.isNotEmpty() }.forEach(viewModel::selectAll) },
                        onInvertSelection = { ActivePane.entries.filter { (if (it == ActivePane.LEFT) leftState else rightState).selectedPaths.isNotEmpty() }.forEach(viewModel::invertSelection) },
                        onSelectSameType = { ActivePane.entries.filter { (if (it == ActivePane.LEFT) leftState else rightState).selectedPaths.isNotEmpty() }.forEach(viewModel::selectSameType) },
                        onExitSelection = { ActivePane.entries.forEach(viewModel::clearSelection) },
                        destinationLabel = if (selectionPane == ActivePane.LEFT) "Rechtes Panel" else "Linkes Panel",
                        enabled = !fileTransfer.running,
                        onCopySelected = { transferRequest = selectionPane to false },
                        onMoveSelected = { transferRequest = selectionPane to true },
                        onDeleteSelected = { requestDelete(selectionPane) },
                        onMoreOptions = { viewModel.setActive(selectionPane); showSelectionMore = true },
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
                                description = "Panels tauschen",
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
                showBookmarkSheet = false
                if (path.isNotBlank()) requestAddBookmark(path)
            },
            onEdit = { bookmark -> showBookmarkSheet = false; bookmarkEditingPath = bookmark.path; bookmarkEditor = bookmark },
            onRemove = { bookmark -> showBookmarkSheet = false; bookmarkToDelete = bookmark },
        )
    }

    bookmarkEditor?.let { bookmark ->
        BookmarkEditorDialog(bookmark, bookmarkEditingPath, bookmarks, onDismiss = { bookmarkEditor = null }, onSave = { updated ->
            val entries = if (bookmarkEditingPath == null) bookmarks + updated
                else bookmarks.map { if (it.path == bookmarkEditingPath) updated else it }
            persistBookmarks(entries)
            bookmarkEditor = null
        })
    }
    bookmarkToDelete?.let { bookmark ->
        BookmarkDeleteDialog(bookmark, onDismiss = { bookmarkToDelete = null }, onDelete = {
            persistBookmarks(bookmarks.filterNot { it.path == bookmark.path })
            bookmarkToDelete = null
        })
    }

    if (showSelectionMore) {
        MtClassicAlertDialog(
            onDismissRequest = { showSelectionMore = false },
            title = { Text("${activeState.selectedPaths.size} ausgewählt") },
            text = {
                Column {
                    TextButton(onClick = {
                        showSelectionMore = false
                        renamePane = activePane
                        renameItems = activeState.filteredItems.filter { it.path in activeState.selectedPaths }
                        showMultiRename = renameItems.isNotEmpty()
                    }) { Text("UMBENENNEN") }
                    TextButton(onClick = { showSelectionMore = false; viewModel.invertSelection(activePane) }) { Text("AUSWAHL UMKEHREN") }
                    TextButton(onClick = {
                        showSelectionMore = false
                        archivePane = activePane
                        val selected = activeState.items.filter { it.path in activeState.selectedPaths }
                        scope.launch {
                            val result = runCatching { selected.map { viewModel.materializeForArchive(it) } }
                            result.onSuccess { archiveSources = it; showArchiveDialog = it.isNotEmpty() }
                                .onFailure { Toast.makeText(context, it.message, Toast.LENGTH_LONG).show() }
                        }
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
        toolTasks = toolTasks,
        onOpenToolTask = { selectedToolTaskId = it },
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
    toolTasks.firstOrNull { it.id == selectedToolTaskId }?.let { task ->
        MtClassicAlertDialog(
            onDismissRequest = { selectedToolTaskId = null },
            title = { Text(task.title) },
            text = { Text(task.log + "\n" + task.message) },
            confirmButton = { TextButton(onClick = { selectedToolTaskId = null }) { Text("SCHLIESSEN") } },
            dismissButton = {
                if (!task.isTerminal && task.canCancel) TextButton(onClick = { ToolTaskRegistry.cancel(task.id) }) { Text("STOPPEN") }
                task.outputPath?.let { output -> TextButton(onClick = { selectedToolTaskId = null; showTaskPanel = false; viewModel.revealOutput(activePane, output) }) { Text("DATEI ANZEIGEN") } }
            },
        )
    }
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
    description: String? = null,
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
            contentDescription = description,
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
