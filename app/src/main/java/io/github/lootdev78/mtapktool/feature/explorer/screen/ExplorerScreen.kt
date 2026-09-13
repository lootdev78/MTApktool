package io.github.lootdev78.mtapktool.feature.explorer.screen

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.os.Environment
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel as composeViewModel
import androidx.navigation.NavHostController
import io.github.lootdev78.mtapktool.apktool.ApktoolBuildDialog
import io.github.lootdev78.mtapktool.apktool.ApktoolCliDialog
import io.github.lootdev78.mtapktool.apktool.ApktoolDecodeDialog
import io.github.lootdev78.mtapktool.apktool.ApktoolJobsDialog
import io.github.lootdev78.mtapktool.apktool.ApktoolJobsViewModel
import io.github.lootdev78.mtapktool.apktool.ApktoolSettingsDialog
import io.github.lootdev78.mtapktool.apktool.isApkLike
import io.github.lootdev78.mtapktool.apktool.isApktoolProject
import io.github.lootdev78.mtapktool.feature.editor.FileInfoDialog
import io.github.lootdev78.mtapktool.feature.explorer.component.ClassicFilePane
import io.github.lootdev78.mtapktool.feature.explorer.component.CustomCreateItemDialog
import io.github.lootdev78.mtapktool.feature.explorer.component.FileContextMenuDialog
import io.github.lootdev78.mtapktool.feature.explorer.component.RenameDialog
import io.github.lootdev78.mtapktool.feature.explorer.component.GoToPathDialog
import io.github.lootdev78.mtapktool.feature.explorer.component.SelectionBottomBar
import io.github.lootdev78.mtapktool.feature.explorer.component.SideBar
import io.github.lootdev78.mtapktool.feature.explorer.model.FileItem
import io.github.lootdev78.mtapktool.feature.explorer.state.isEditableTextFile
import io.github.lootdev78.mtapktool.feature.explorer.state.isImageFile
import io.github.lootdev78.mtapktool.feature.explorer.viewmodel.ActivePane
import io.github.lootdev78.mtapktool.feature.explorer.viewmodel.ExplorerViewModel
import io.github.lootdev78.mtapktool.feature.explorer.util.FileOpener
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

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    val rootPath = Environment.getExternalStorageDirectory().absolutePath
    val activeState = if (activePane == ActivePane.LEFT) leftState else rightState
    val context = navController.context
    val bookmarkPreferences = remember(context) {
        context.getSharedPreferences("explorer_bookmarks", Context.MODE_PRIVATE)
    }
    var bookmarks by remember(bookmarkPreferences) {
        mutableStateOf(bookmarkPreferences.getStringSet("paths", emptySet()).orEmpty().toList().sorted())
    }

    LaunchedEffect(viewModel, context) {
        viewModel.operationMessages.collect { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    // State Variables
    var showContextMenu by remember { mutableStateOf(false) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var showGoToPathDialog by remember { mutableStateOf(false) }
    var showPropertyDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var targetItem by remember { mutableStateOf<FileItem?>(null) }
    var isSearching by remember { mutableStateOf(false) }
    var showApktoolDecode by remember { mutableStateOf(false) }
    var showApktoolBuild by remember { mutableStateOf(false) }
    var showApktoolSettings by remember { mutableStateOf(false) }
    var showApktoolJobs by remember { mutableStateOf(false) }
    var showApktoolCli by remember { mutableStateOf(false) }
    var showApktoolOptions by remember { mutableStateOf(false) }
    var apktoolTarget by remember { mutableStateOf<File?>(null) }

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
                targetItem?.let { item -> viewModel.compressItem(activePane, item.path) }
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
            onApktool = {
                targetItem?.let { item ->
                    val file = File(item.path)
                    apktoolTarget = file
                    if (isApktoolProject(file)) showApktoolBuild = true
                    else if (isApkLike(file)) showApktoolDecode = true
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
        GoToPathDialog(
            initialPath = activeState.currentPath,
            onDismiss = { showGoToPathDialog = false },
            onGo = { path ->
                viewModel.navigateToDirectPath(activePane, path)
                showGoToPathDialog = false
            }
        )
    }

    if (showApktoolDecode) {
        apktoolTarget?.takeIf(::isApkLike)?.let { file ->
            ApktoolDecodeDialog(file = file, onDismiss = { showApktoolDecode = false })
        } ?: run { showApktoolDecode = false }
    }

    if (showApktoolBuild) {
        apktoolTarget?.takeIf(::isApktoolProject)?.let { project ->
            ApktoolBuildDialog(project = project, onDismiss = { showApktoolBuild = false })
        } ?: run { showApktoolBuild = false }
    }

    if (showApktoolSettings) {
        ApktoolSettingsDialog(onDismiss = { showApktoolSettings = false })
    }

    if (showApktoolJobs) {
        ApktoolJobsDialog(
            jobs = apktoolJobs,
            onCancel = apktoolJobsViewModel::cancel,
            onCancelAll = apktoolJobsViewModel::cancelAll,
            onDismiss = { showApktoolJobs = false },
        )
    }

    if (showApktoolCli) {
        ApktoolCliDialog(onDismiss = { showApktoolCli = false })
    }

    BackHandler(enabled = canNavigateBack || isSearching) {
        if (isSearching) {
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
                        onBookmarkClick = { path -> viewModel.navigateToDirectPath(activePane, path) },
                        onRemoveBookmark = { path ->
                            val updated = bookmarks.filterNot { it == path }
                            bookmarks = updated
                            bookmarkPreferences.edit().putStringSet("paths", updated.toSet()).apply()
                        },
                        onOpenSettings = { showApktoolSettings = true },
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

                            Column(modifier = Modifier.weight(1f)) {
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
                                        text = { Text("Erstellen & Dekodieren") },
                                        onClick = { showApktoolOptions = false; showApktoolSettings = true }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Apktool Jobs (${apktoolJobs.count { !it.isTerminal }})") },
                                        onClick = { showApktoolOptions = false; showApktoolJobs = true }
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
                        onItemClick = { item ->
                            if (leftState.selectedPaths.isNotEmpty()) {
                                viewModel.toggleSelection(ActivePane.LEFT, item.path, false)
                            } else {
                                when {
                                    isApkLike(File(item.path)) -> {
                                        apktoolTarget = File(item.path)
                                        showApktoolDecode = true
                                    }

                                    item.isDirectory -> viewModel.loadDirectory(
                                        ActivePane.LEFT,
                                        item.path
                                    )

                                    item.isEditableTextFile() -> {
                                        navController.navigate(
                                            Screen.Editor.createRoute(
                                                item.path,
                                                item.name
                                            )
                                        )
                                    }

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
                        onItemClick = { item ->
                            if (rightState.selectedPaths.isNotEmpty()) {
                                viewModel.toggleSelection(ActivePane.RIGHT, item.path, false)
                            } else {
                                when {
                                    isApkLike(File(item.path)) -> {
                                        apktoolTarget = File(item.path)
                                        showApktoolDecode = true
                                    }

                                    item.isDirectory -> viewModel.loadDirectory(
                                        ActivePane.RIGHT,
                                        item.path
                                    )

                                    item.isEditableTextFile() -> {
                                        navController.navigate(
                                            Screen.Editor.createRoute(
                                                item.path,
                                                item.name
                                            )
                                        )
                                    }

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
                        onMoreOptions = { viewModel.moveSelectedToOppositePane(activePane) }
                    )
                } else {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        shadowElevation = 8.dp,
                        modifier = Modifier.fillMaxWidth()
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
                                onClick = { showGoToPathDialog = true }
                            )
                        }
                    }
                }
            }
        }
    }
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