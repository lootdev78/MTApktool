package io.github.lootdev78.mtapktool.feature.explorer.screen

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.os.Environment
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel as composeViewModel
import androidx.navigation.NavHostController
import io.github.lootdev78.mtapktool.apktool.ApktoolBuildDialog
import io.github.lootdev78.mtapktool.apktool.ApktoolDecodeDialog
import io.github.lootdev78.mtapktool.apktool.ApktoolFrameworkImportDialog
import io.github.lootdev78.mtapktool.apktool.ApktoolJobOutputDialog
import io.github.lootdev78.mtapktool.apktool.ApktoolJobsViewModel
import io.github.lootdev78.mtapktool.apktool.ApkInfoActivity
import io.github.lootdev78.mtapktool.apktool.ApktoolTaskPanel
import io.github.lootdev78.mtapktool.apktool.SplitPackageActivity
import io.github.lootdev78.mtapktool.apktool.isApkLike
import io.github.lootdev78.mtapktool.apktool.isApktoolProject
import io.github.lootdev78.mtapktool.archive.ArchiveActionDialog
import io.github.lootdev78.mtapktool.archive.ArchiveCreateDialog
import io.github.lootdev78.mtapktool.archive.ArchiveEngine
import io.github.lootdev78.mtapktool.archive.ArchiveExtractDialog
import io.github.lootdev78.mtapktool.feature.editor.FileInfoDialog
import io.github.lootdev78.mtapktool.feature.explorer.component.ClassicFilePane
import io.github.lootdev78.mtapktool.feature.explorer.component.EditHiddenFilesDialog
import io.github.lootdev78.mtapktool.feature.explorer.component.ExplorerSideDrawer
import io.github.lootdev78.mtapktool.feature.explorer.component.FileFilterDialog
import io.github.lootdev78.mtapktool.feature.explorer.component.HiddenFilesDialog
import io.github.lootdev78.mtapktool.feature.explorer.component.SortFilesDialog
import io.github.lootdev78.mtapktool.feature.explorer.component.fileFilterLabel
import io.github.lootdev78.mtapktool.feature.explorer.component.CustomCreateItemDialog
import io.github.lootdev78.mtapktool.feature.explorer.component.FileContextMenuDialog
import io.github.lootdev78.mtapktool.feature.explorer.component.RenameDialog
import io.github.lootdev78.mtapktool.feature.explorer.component.GoToPathDialog
import io.github.lootdev78.mtapktool.feature.explorer.component.SelectionBottomBar
import io.github.lootdev78.mtapktool.feature.explorer.component.SideBar
import io.github.lootdev78.mtapktool.feature.explorer.model.CustomLocationStore
import io.github.lootdev78.mtapktool.feature.explorer.model.ExplorerPreferenceStore
import io.github.lootdev78.mtapktool.feature.explorer.model.FileItem
import io.github.lootdev78.mtapktool.feature.explorer.state.FileFilter
import io.github.lootdev78.mtapktool.feature.explorer.state.isArchiveFile
import io.github.lootdev78.mtapktool.feature.explorer.state.isEditableTextFile
import io.github.lootdev78.mtapktool.feature.explorer.state.isImageFile
import io.github.lootdev78.mtapktool.feature.explorer.viewmodel.ActivePane
import io.github.lootdev78.mtapktool.feature.explorer.viewmodel.ExplorerViewModel
import io.github.lootdev78.mtapktool.feature.explorer.util.FileOpener
import io.github.lootdev78.mtapktool.settings.ApktoolCliActivity
import io.github.lootdev78.mtapktool.settings.ApktoolSettingsActivity
import io.github.lootdev78.mtapktool.settings.SettingsActivity
import io.github.lootdev78.mtapktool.tools.ApkExtractorActivity
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun ExplorerScreen(
    navController: NavHostController,
    viewModel: ExplorerViewModel = composeViewModel()
) {
    val leftState by viewModel.leftPaneState.collectAsState()
    val rightState by viewModel.rightPaneState.collectAsState()
    val activePane by viewModel.activePane.collectAsState()
    val apktoolJobsViewModel: ApktoolJobsViewModel = composeViewModel()
    val apktoolJobs by apktoolJobsViewModel.jobs.collectAsState()
    var observedSuccessfulJobs by remember { mutableStateOf<Set<String>>(emptySet()) }

    var showLeftDrawer by remember { mutableStateOf(false) }
    var leftDrawerProgress by remember { mutableFloatStateOf(0f) }
    var leftDrawerDragging by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val rootPath = Environment.getExternalStorageDirectory().absolutePath
    val activeState = if (activePane == ActivePane.LEFT) leftState else rightState
    val context = navController.context
    val lifecycleOwner = LocalLifecycleOwner.current
    val restoredExplorerOptions = remember(context) { ExplorerPreferenceStore.load(context) }

    LaunchedEffect(viewModel, restoredExplorerOptions) {
        val o = restoredExplorerOptions
        viewModel.restoreExplorerOptions(
            leftShowSystemHidden = o.leftSystemHidden,
            rightShowSystemHidden = o.rightSystemHidden,
            leftShowManuallyHidden = o.leftManualHidden,
            rightShowManuallyHidden = o.rightManualHidden,
            hiddenPaths = o.manualHiddenPaths,
            leftSort = o.leftSort,
            rightSort = o.rightSort,
            leftFilter = o.leftFilter,
            rightFilter = o.rightFilter,
            sortOverrides = o.folderSortOverrides,
        )
    }

    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    viewModel.commitMountedArchives()
                    // External editors/installers may have changed files while Explorer was paused.
                    viewModel.refreshDirectory(ActivePane.LEFT)
                    viewModel.refreshDirectory(ActivePane.RIGHT)
                }
                Lifecycle.Event.ON_STOP -> viewModel.commitMountedArchives()
                else -> Unit
            }
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
    var customLocations by remember(context) { mutableStateOf(CustomLocationStore.all(context)) }
    var launchedPackagePane by remember { mutableStateOf(ActivePane.LEFT) }
    val addLocationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }
            CustomLocationStore.add(context, uri)
            customLocations = CustomLocationStore.all(context)
        }
    }
    val apkInfoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val data = result.data
        if (result.resultCode == android.app.Activity.RESULT_OK && data?.getStringExtra(ApkInfoActivity.RESULT_ACTION) == ApkInfoActivity.ACTION_VIEW_ARCHIVE) {
            data.getStringExtra(ApkInfoActivity.EXTRA_PATH)?.let(::File)?.takeIf { it.isFile }?.let { viewModel.openArchive(launchedPackagePane, it) }
        }
    }
    fun openAppPackage(file: File, pane: ActivePane) {
        launchedPackagePane = pane
        if (file.extension.equals("apk", true)) {
            apkInfoLauncher.launch(
                Intent(context, ApkInfoActivity::class.java)
                    .putExtra(ApkInfoActivity.EXTRA_PATH, file.absolutePath)
                    .putExtra(ApkInfoActivity.EXTRA_LEFT_PATH, leftState.currentPath)
                    .putExtra(ApkInfoActivity.EXTRA_RIGHT_PATH, rightState.currentPath)
                    .putExtra(ApkInfoActivity.EXTRA_SOURCE_PANE, pane.name)
            )
        } else {
            context.startActivity(
                Intent(context, SplitPackageActivity::class.java)
                    .putExtra(SplitPackageActivity.EXTRA_PATH, file.absolutePath)
                    .putExtra(SplitPackageActivity.EXTRA_LEFT_PATH, leftState.currentPath)
                    .putExtra(SplitPackageActivity.EXTRA_RIGHT_PATH, rightState.currentPath)
                    .putExtra(SplitPackageActivity.EXTRA_SOURCE_PANE, pane.name)
            )
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
            viewModel.refreshDirectory(ActivePane.LEFT)
            viewModel.refreshDirectory(ActivePane.RIGHT)
        }
    }

    // State Variables
    var showContextMenu by remember { mutableStateOf(false) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var showGoToPathDialog by remember { mutableStateOf(false) }
    var goToPane by remember { mutableStateOf<ActivePane?>(null) }
    var showPropertyDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var targetItem by remember { mutableStateOf<FileItem?>(null) }
    var isSearching by remember { mutableStateOf(false) }
    var showApktoolDecode by remember { mutableStateOf(false) }
    var showApktoolBuild by remember { mutableStateOf(false) }
    var showTaskPanel by remember { mutableStateOf(false) }
    var taskDrawerProgress by remember { mutableFloatStateOf(0f) }
    var taskDrawerDragging by remember { mutableStateOf(false) }
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
    var sortManageRevision by remember { mutableIntStateOf(0) }
    var showFilterFiles by remember { mutableStateOf(false) }

    val hasSelectedItems = activeState.selectedPaths.isNotEmpty()
    val canNavigateBack = activeState.currentPath != rootPath && activeState.currentPath != "/"

    // Dialogs
    if (showContextMenu && targetItem != null) {
        FileContextMenuDialog(
            targetItem=targetItem,
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
            onLink = {
                targetItem?.let { item -> viewModel.linkToOppositePane(activePane, item.path) }
                showContextMenu = false
            },
            onRename = {
                showRenameDialog = true
                showContextMenu = false
            },
            onDelete = {
                targetItem?.let { item ->
                    viewModel.ensureSelected(activePane, item.path)
                    viewModel.deleteSelected(activePane)
                }
                showContextMenu = false
            },
            onCompress = {
                targetItem?.let { item ->
                    archiveSources = listOf(File(item.path))
                    archivePane = activePane
                    showArchiveDialog = true
                }
                showContextMenu = false
            },
            onProperty = {
                showPropertyDialog = true
                showContextMenu = false
            },
            onShare = {
                targetItem?.let { item ->
                    val file = File(item.path)
                    if (file.isFile) {
                        runCatching {
                            val uri = FileProvider.getUriForFile(
                                context,
                                "${context.packageName}.fileprovider",
                                file
                            )
                            val mimeType = MimeTypeMap.getSingleton()
                                .getMimeTypeFromExtension(file.extension.lowercase()) ?: "*/*"
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = mimeType
                                putExtra(Intent.EXTRA_STREAM, uri)
                                clipData = ClipData.newRawUri(file.name, uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(intent, "Share File"))
                        }.onFailure { error ->
                            Toast.makeText(context, "Share failed: ${error.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                showContextMenu = false
            },
            onOpenWith = {
                targetItem?.let { item ->
                    val file = File(item.path)
                    if (file.isFile) FileOpener.openFile(context, file)
                }
                showContextMenu = false
            },
            onAddBookmark = {
                targetItem?.let { item ->
                    val updated = (bookmarks + item.path).distinct().sorted()
                    bookmarks = updated
                    bookmarkPreferences.edit().putStringSet("paths", updated.toSet()).apply()
                    Toast.makeText(context, "Bookmark added", Toast.LENGTH_SHORT).show()
                }
                showContextMenu = false
            },
            readOnlyArchive = activeState.isArchiveView,
            onApktool = {
                targetItem?.let { item ->
                    val file = File(item.path)
                    apktoolTarget = file
                    if (isApktoolProject(file)) {
                        showApktoolBuild = true
                    } else if (isApkLike(file)) {
                        openAppPackage(file, activePane)
                    }
                }
                showContextMenu = false
            },
            onExtractArchive = {
                targetItem?.let { item ->
                    val file = File(item.path)
                    if (ArchiveEngine.supports(file)) {
                        archiveTarget = file
                        archiveActionPane = activePane
                        archiveActionPassword = ""
                        showArchiveExtract = true
                    }
                }
                showContextMenu = false
            },

        )
    }

    if (showPropertyDialog && targetItem != null) {
        FileInfoDialog(
            file = File(targetItem!!.path),
            onDismiss = { showPropertyDialog = false }
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
            onShowSystemHidden = { show ->
                viewModel.setShowSystemHidden(activePane, show)
                ExplorerPreferenceStore.saveVisibility(context, activePane, systemHidden = show)
            },
            onShowManuallyHidden = { show ->
                viewModel.setShowManuallyHidden(activePane, show)
                ExplorerPreferenceStore.saveVisibility(context, activePane, manualHidden = show)
            },
            onHideSelected = {
                viewModel.hideSelectedManually(activePane)
                ExplorerPreferenceStore.saveManualHiddenPaths(context, viewModel.manualHiddenPaths().toSet())
            },
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
            onRemove = { path ->
                viewModel.unhideManualPath(path)
                ExplorerPreferenceStore.saveManualHiddenPaths(context, viewModel.manualHiddenPaths().toSet())
            },
            onClear = {
                viewModel.clearManualHidden()
                ExplorerPreferenceStore.saveManualHiddenPaths(context, emptySet())
            },
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
            onApply = { spec, onlyFolder ->
                viewModel.setSort(activePane, spec, onlyFolder)
                if (!onlyFolder) ExplorerPreferenceStore.saveDefaultSort(context, activePane, spec)
                ExplorerPreferenceStore.saveFolderSortOverrides(context, viewModel.folderSortOverridesSnapshot())
            },
            onDismiss = { showSortFiles = false },
        )
    }

    if (showSortManage) {
        val panePrefix = activePane.name + ":"
        val overrides = remember(showSortManage, sortManageRevision, activePane) {
            viewModel.folderSortOverridesSnapshot()
                .filterKeys { it.startsWith(panePrefix) }
                .mapKeys { it.key.removePrefix(panePrefix) }
                .toSortedMap(String.CASE_INSENSITIVE_ORDER)
        }
        AlertDialog(
            onDismissRequest = { showSortManage = false },
            title = { Text("Sortierung verwalten") },
            text = {
                if (overrides.isEmpty()) {
                    Text("Keine ordnerspezifischen Sortierungen für ${if (activePane == ActivePane.LEFT) "das linke" else "das rechte"} Fenster.")
                } else {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        overrides.forEach { (path, spec) ->
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(path, maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                                    Text(
                                        "${spec.field.name.lowercase().replaceFirstChar { it.uppercase() }} • ${if (spec.descending) "absteigend" else "aufsteigend"}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                TextButton(onClick = {
                                    viewModel.removeFolderSortOverride(activePane, path)
                                    ExplorerPreferenceStore.saveFolderSortOverrides(context, viewModel.folderSortOverridesSnapshot())
                                    sortManageRevision++
                                }) { Text("ENTFERNEN") }
                            }
                        }
                    }
                }
            },
            dismissButton = {
                if (overrides.isNotEmpty()) {
                    TextButton(onClick = {
                        viewModel.clearFolderSortOverrides(activePane)
                        ExplorerPreferenceStore.saveFolderSortOverrides(context, viewModel.folderSortOverridesSnapshot())
                        sortManageRevision++
                    }) { Text("ALLE ENTFERNEN") }
                }
            },
            confirmButton = { TextButton(onClick = { showSortManage = false }) { Text("FERTIG") } },
        )
    }

    if (showFilterFiles) {
        FileFilterDialog(
            current = activeState.filter,
            onApply = { filter ->
                viewModel.setFilter(activePane, filter)
                ExplorerPreferenceStore.saveFilter(context, activePane, filter)
            },
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

    if (showApktoolDecode) {
        apktoolTarget?.takeIf(::isApkLike)?.let { file ->
            ApktoolDecodeDialog(file = file, leftPanelPath = leftState.currentPath, rightPanelPath = rightState.currentPath, onDismiss = { showApktoolDecode = false }, onJobQueued = { jobId -> selectedJobId = jobId })
        } ?: run { showApktoolDecode = false }
    }

    if (showApktoolBuild) {
        apktoolTarget?.takeIf(::isApktoolProject)?.let { project ->
            ApktoolBuildDialog(project = project, leftPanelPath = leftState.currentPath, rightPanelPath = rightState.currentPath, onDismiss = { showApktoolBuild = false }, onJobQueued = { jobId -> selectedJobId = jobId })
        } ?: run { showApktoolBuild = false }
    }


    selectedJobId?.let { jobId ->
        ApktoolJobOutputDialog(
            jobId = jobId,
            job = apktoolJobs.firstOrNull { it.id == jobId },
            onHide = { selectedJobId = null },
            onCancel = apktoolJobsViewModel::cancel,
        )
    }

    BackHandler(enabled = showLeftDrawer || showTaskPanel || selectedJobId != null || canNavigateBack || isSearching) {
        if (selectedJobId != null) {
            selectedJobId = null
        } else if (showTaskPanel) {
            taskDrawerDragging = false
            taskDrawerProgress = 0f
            showTaskPanel = false
        } else if (showLeftDrawer) {
            leftDrawerDragging = false
            leftDrawerProgress = 0f
            showLeftDrawer = false
        } else if (isSearching) {
            isSearching = false
            viewModel.clearSearch(activePane)
        } else {
            viewModel.navigateUp(activePane)
        }
    }

    BoxWithConstraints {
        val drawerWidth = maxWidth * 0.7f
        Box(Modifier.fillMaxSize()) {
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
                        Box(Modifier.fillMaxWidth()) {
                            SearchBar(
                                query = activeState.searchQuery,
                                onQueryChange = { viewModel.setSearchQuery(activePane, it) },
                                onOpenDrawer = { showLeftDrawer = true },
                                onMore = { showApktoolOptions = true },
                                onClose = {
                                    isSearching = false
                                    viewModel.clearSearch(activePane)
                                }
                            )
                            DropdownMenu(
                                expanded = showApktoolOptions,
                                onDismissRequest = { showApktoolOptions = false },
                                modifier = Modifier.align(Alignment.TopEnd),
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Versteckte Dateien") },
                                    leadingIcon = { Icon(Icons.Default.Visibility, contentDescription = null) },
                                    onClick = { showApktoolOptions = false; showHiddenFiles = true },
                                )
                                DropdownMenuItem(
                                    text = { Text("Sortieren") },
                                    leadingIcon = { Icon(Icons.Default.Sort, contentDescription = null) },
                                    onClick = { showApktoolOptions = false; showSortFiles = true },
                                )
                                DropdownMenuItem(
                                    text = { Text(if (activeState.filter == FileFilter.ALL) "Filter" else "Filter: ${fileFilterLabel(activeState.filter)}") },
                                    leadingIcon = { Icon(Icons.Default.FilterList, contentDescription = null) },
                                    onClick = { showApktoolOptions = false; showFilterFiles = true },
                                )
                                DropdownMenuItem(
                                    text = { Text("Erstellen & Dekodieren") },
                                    onClick = { showApktoolOptions = false; context.startActivity(Intent(context, ApktoolSettingsActivity::class.java)) },
                                )
                                DropdownMenuItem(
                                    text = { Text("Apktool Jobs (${apktoolJobs.count { !it.isTerminal }})") },
                                    onClick = { showApktoolOptions = false; showTaskPanel = true; taskDrawerProgress = 1f },
                                )
                                DropdownMenuItem(
                                    text = { Text("Apktool CLI") },
                                    onClick = { showApktoolOptions = false; context.startActivity(Intent(context, ApktoolCliActivity::class.java)) },
                                )
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = { showLeftDrawer = true },
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
                                    text = activeState.currentPath,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "Folder: ${activeState.folderCount} File: ${activeState.fileCount}",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
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
                                        leadingIcon = { Icon(Icons.Default.Sort, contentDescription = null) },
                                        onClick = { showApktoolOptions = false; showSortFiles = true },
                                    )
                                    DropdownMenuItem(
                                        text = { Text(if (activeState.filter == FileFilter.ALL) "Filter" else "Filter: ${fileFilterLabel(activeState.filter)}") },
                                        leadingIcon = { Icon(Icons.Default.FilterList, contentDescription = null) },
                                        onClick = { showApktoolOptions = false; showFilterFiles = true },
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Erstellen & Dekodieren") },
                                        onClick = { showApktoolOptions = false; context.startActivity(Intent(context, ApktoolSettingsActivity::class.java)) }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Apktool Jobs (${apktoolJobs.count { !it.isTerminal }})") },
                                        onClick = { showApktoolOptions = false; showTaskPanel = true }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Apktool CLI") },
                                        onClick = { showApktoolOptions = false; context.startActivity(Intent(context, ApktoolCliActivity::class.java)) }
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
                                    isApkLike(File(item.path)) -> {
                                        val apk = File(item.path)
                                        apktoolTarget = apk
                                        targetItem = item
                                        openAppPackage(apk, ActivePane.LEFT)
                                    }

                                    item.isArchiveFile() && ArchiveEngine.supports(File(item.path)) -> {
                                        archiveTarget = File(item.path)
                                        archiveActionPane = ActivePane.LEFT
                                        archiveActionPassword = ""
                                        showArchiveActions = true
                                    }

                                    item.isDirectory -> viewModel.loadDirectory(
                                        ActivePane.LEFT,
                                        item.path
                                    )

                                    item.isEditableTextFile() -> FileOpener.openFile(navController.context, File(item.path))

                                    item.isImageFile() -> {
                                        navController.navigate(
                                            Screen.ImageViewer.createRoute(
                                                item.path,
                                                item.name
                                            )
                                        )
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
                                    isApkLike(File(item.path)) -> {
                                        val apk = File(item.path)
                                        apktoolTarget = apk
                                        targetItem = item
                                        openAppPackage(apk, ActivePane.RIGHT)
                                    }

                                    item.isArchiveFile() && ArchiveEngine.supports(File(item.path)) -> {
                                        archiveTarget = File(item.path)
                                        archiveActionPane = ActivePane.RIGHT
                                        archiveActionPassword = ""
                                        showArchiveActions = true
                                    }

                                    item.isDirectory -> viewModel.loadDirectory(
                                        ActivePane.RIGHT,
                                        item.path
                                    )

                                    item.isEditableTextFile() -> FileOpener.openFile(navController.context, File(item.path))

                                    item.isImageFile() -> {
                                        navController.navigate(
                                            Screen.ImageViewer.createRoute(
                                                item.path,
                                                item.name
                                            )
                                        )
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
                        onInvertSelection = { viewModel.invertSelection(activePane) },
                        onDeleteSelected = { viewModel.deleteSelected(activePane) },
                        onCloseSelected = { viewModel.cancelSelection(activePane) },
                        onArchiveSelected = {
                            archivePane = activePane
                            archiveSources = activeState.selectedPaths.map(::File)
                            showArchiveDialog = archiveSources.isNotEmpty()
                        },
                        onMoreOptions = { viewModel.moveSelectedToOppositePane(activePane) },
                        modifier = Modifier.drawerSwipe(
                            onLeftDragStart = {
                                leftDrawerDragging = true
                                leftDrawerProgress = if (showLeftDrawer) 1f else 0f
                            },
                            onLeftDragProgress = { leftDrawerProgress = it },
                            onLeftDragEnd = { open ->
                                leftDrawerDragging = false
                                showLeftDrawer = open
                            },
                            onRightDragStart = {
                                taskDrawerDragging = true
                                taskDrawerProgress = if (showTaskPanel) 1f else 0f
                            },
                            onRightDragProgress = { taskDrawerProgress = it },
                            onRightDragEnd = { open ->
                                taskDrawerDragging = false
                                showTaskPanel = open
                            },
                        ),
                    )
                } else {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        shadowElevation = 8.dp,
                        modifier = Modifier.fillMaxWidth().drawerSwipe(
                            onLeftDragStart = {
                                leftDrawerDragging = true
                                leftDrawerProgress = if (showLeftDrawer) 1f else 0f
                            },
                            onLeftDragProgress = { leftDrawerProgress = it },
                            onLeftDragEnd = { open ->
                                leftDrawerDragging = false
                                showLeftDrawer = open
                            },
                            onRightDragStart = {
                                taskDrawerDragging = true
                                taskDrawerProgress = if (showTaskPanel) 1f else 0f
                            },
                            onRightDragProgress = { taskDrawerProgress = it },
                            onRightDragEnd = { open ->
                                taskDrawerDragging = false
                                showTaskPanel = open
                            },
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
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

            ExplorerSideDrawer(
                visible = showLeftDrawer,
                drawerWidth = drawerWidth,
                dragProgress = leftDrawerProgress,
                dragging = leftDrawerDragging,
                onDragProgress = { progress ->
                    leftDrawerDragging = true
                    leftDrawerProgress = progress
                },
                onDragSettled = { open ->
                    leftDrawerDragging = false
                    showLeftDrawer = open
                },
                onDismiss = {
                    leftDrawerDragging = false
                    leftDrawerProgress = 0f
                    showLeftDrawer = false
                },
            ) {
                SideBar(
                    drawerWidth = drawerWidth,
                    viewModel = viewModel,
                    bookmarks = bookmarks,
                    customLocations = customLocations,
                    onBookmarkClick = { path -> viewModel.navigateToDirectPath(activePane, path) },
                    onRemoveBookmark = { path ->
                        val updated = bookmarks.filterNot { it == path }
                        bookmarks = updated
                        bookmarkPreferences.edit().putStringSet("paths", updated.toSet()).apply()
                    },
                    onCustomLocationClick = { uri ->
                        context.startActivity(Intent(context, SafTreeBrowserActivity::class.java).putExtra(SafTreeBrowserActivity.EXTRA_TREE_URI, uri.toString()))
                    },
                    onAddLocation = { addLocationLauncher.launch(null) },
                    onOpenApkExtractor = { context.startActivity(Intent(context, ApkExtractorActivity::class.java)) },
                    onOpenSettings = { context.startActivity(Intent(context, SettingsActivity::class.java)) },
                    onClose = {
                        leftDrawerDragging = false
                        leftDrawerProgress = 0f
                        showLeftDrawer = false
                    },
                )
            }
        }
    }

    ApktoolTaskPanel(
        visible = showTaskPanel,
        jobs = apktoolJobs,
        dragProgress = taskDrawerProgress,
        dragging = taskDrawerDragging,
        onDragProgress = { progress ->
            taskDrawerDragging = true
            taskDrawerProgress = progress
        },
        onDragSettled = { open ->
            taskDrawerDragging = false
            showTaskPanel = open
        },
        onOpenJob = { jobId ->
            taskDrawerDragging = false
            taskDrawerProgress = 0f
            showTaskPanel = false
            selectedJobId = jobId
        },
        onCancel = apktoolJobsViewModel::cancel,
        onCancelAll = apktoolJobsViewModel::cancelAll,
        onRemove = apktoolJobsViewModel::dismiss,
        onClearFinished = apktoolJobsViewModel::clearFinished,
        onDismiss = {
            taskDrawerDragging = false
            taskDrawerProgress = 0f
            showTaskPanel = false
        },
    )
}

private fun Modifier.drawerSwipe(
    onLeftDragStart: () -> Unit,
    onLeftDragProgress: (Float) -> Unit,
    onLeftDragEnd: (Boolean) -> Unit,
    onRightDragStart: () -> Unit,
    onRightDragProgress: (Float) -> Unit,
    onRightDragEnd: (Boolean) -> Unit,
): Modifier = pointerInput(
    onLeftDragStart,
    onLeftDragProgress,
    onLeftDragEnd,
    onRightDragStart,
    onRightDragProgress,
    onRightDragEnd,
) {
    var startX = 0f
    var total = 0f
    var leftActive = false
    var rightActive = false
    var lastTime = 0L
    var velocityX = 0f
    detectHorizontalDragGestures(
        onDragStart = { offset ->
            startX = offset.x
            total = 0f
            lastTime = 0L
            velocityX = 0f
            leftActive = startX <= size.width * 0.32f
            rightActive = !leftActive && startX >= size.width * 0.68f
            if (leftActive) onLeftDragStart()
            if (rightActive) onRightDragStart()
        },
        onHorizontalDrag = { change, amount ->
            if (!leftActive && !rightActive) return@detectHorizontalDragGestures
            total += amount
            if (lastTime != 0L) {
                val dt = (change.uptimeMillis - lastTime).coerceAtLeast(1L)
                val instantaneous = amount * 1000f / dt.toFloat()
                velocityX = velocityX * 0.55f + instantaneous * 0.45f
            }
            lastTime = change.uptimeMillis
            change.consume()
            if (leftActive) {
                onLeftDragProgress((total / (size.width * 0.70f)).coerceIn(0f, 1f))
            } else if (rightActive) {
                onRightDragProgress((-total / (size.width * 0.82f)).coerceIn(0f, 1f))
            }
        },
        onDragEnd = {
            if (leftActive) {
                val progress = (total / (size.width * 0.70f)).coerceIn(0f, 1f)
                onLeftDragEnd(when { velocityX > 900f -> true; velocityX < -900f -> false; else -> progress >= 0.34f })
            } else if (rightActive) {
                val progress = (-total / (size.width * 0.82f)).coerceIn(0f, 1f)
                onRightDragEnd(when { velocityX < -900f -> true; velocityX > 900f -> false; else -> progress >= 0.34f })
            }
            total = 0f
            lastTime = 0L
            velocityX = 0f
            leftActive = false
            rightActive = false
        },
        onDragCancel = {
            if (leftActive) onLeftDragEnd(false)
            if (rightActive) onRightDragEnd(false)
            total = 0f
            leftActive = false
            rightActive = false
        },
    )
}

@Composable
fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onOpenDrawer: () -> Unit,
    onMore: () -> Unit,
    onClose: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onOpenDrawer) {
            Icon(Icons.Default.Menu, contentDescription = "Menü")
        }
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.weight(1f),
            textStyle = LocalTextStyle.current.copy(
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 16.sp,
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.onSurfaceVariant),
            singleLine = true,
            decorationBox = { innerTextField ->
                if (query.isEmpty()) {
                    Text(
                        "Dateien suchen…",
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    )
                }
                innerTextField()
            },
        )
        IconButton(onClick = onClose) {
            Icon(Icons.Default.Close, contentDescription = "Suche schließen")
        }
        IconButton(onClick = onMore) {
            Icon(Icons.Default.MoreVert, contentDescription = "Mehr")
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
