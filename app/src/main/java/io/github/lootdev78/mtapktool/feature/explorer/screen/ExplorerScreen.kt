package io.github.lootdev78.mtapktool.feature.explorer.screen

import android.content.Intent
import android.net.Uri
import android.os.Environment
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
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.core.content.FileProvider
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
import io.github.lootdev78.mtapktool.apktool.ApktoolBuildDialog
import io.github.lootdev78.mtapktool.apktool.ApktoolCliDialog
import io.github.lootdev78.mtapktool.apktool.ApktoolDecodeDialog
import io.github.lootdev78.mtapktool.apktool.ApktoolJobsDialog
import io.github.lootdev78.mtapktool.apktool.ApktoolJobsViewModel
import io.github.lootdev78.mtapktool.apktool.ApktoolSettingsDialog
import io.github.lootdev78.mtapktool.apktool.isApkLike
import io.github.lootdev78.mtapktool.apktool.isApktoolProject
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun ExplorerScreen(
    navController: NavHostController,
    viewModel: ExplorerViewModel = viewModel()
) {
    val leftState by viewModel.leftPaneState.collectAsState()
    val rightState by viewModel.rightPaneState.collectAsState()
    val activePane by viewModel.activePane.collectAsState()
    val apktoolJobsViewModel: ApktoolJobsViewModel = viewModel()
    val apktoolJobs by apktoolJobsViewModel.jobs.collectAsState()

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    val rootPath = Environment.getExternalStorageDirectory().absolutePath
    val activeState = if (activePane == ActivePane.LEFT) leftState else rightState
    val activeApktoolJobCount = apktoolJobs.count { !it.isTerminal }

    // State Variables
    var showContextMenu by remember { mutableStateOf(false) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var showGoToPathDialog by remember { mutableStateOf(false) }
    var showPropertyDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var targetItem by remember { mutableStateOf<FileItem?>(null) }
    var apktoolTarget by remember { mutableStateOf<File?>(null) }
    var showApktoolDecode by remember { mutableStateOf(false) }
    var showApktoolBuild by remember { mutableStateOf(false) }
    var showApktoolSettings by remember { mutableStateOf(false) }
    var showApktoolJobs by remember { mutableStateOf(false) }
    var showApktoolCli by remember { mutableStateOf(false) }
    var showMainMenu by remember { mutableStateOf(false) }
    var isSearching by remember { mutableStateOf(false) }

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
                    viewModel.toggleSelection(activePane, item.path, false)
                    viewModel.copySelectedToOppositePane(activePane)
                }
                showContextMenu = false
            },
            onMove = {
                targetItem?.let { item ->
                    viewModel.toggleSelection(activePane, item.path, false)
                    viewModel.moveSelectedToOppositePane(activePane)
                }
                showContextMenu = false
            },
            onLink = { showContextMenu = false },
            onRename = {
                showRenameDialog = true
                showContextMenu = false
            },
            onDelete = {
                targetItem?.let { item ->
                    viewModel.toggleSelection(activePane, item.path, false)
                    viewModel.deleteSelected(activePane)
                }
                showContextMenu = false
            },
            onCompress = { showContextMenu = false },
            onProperty = {
                showPropertyDialog = true
                showContextMenu = false
            },
            onShare = {
                targetItem?.let { item ->
                    val file = File(item.path)
                    if (file.exists()) {
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "*/*"
                            val uri = FileProvider.getUriForFile(
                                navController.context,
                                "${navController.context.packageName}.fileprovider",
                                file,
                            )
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        navController.context.startActivity(Intent.createChooser(intent, "Share File"))
                    }
                }
                showContextMenu = false
            },
            onAddBookmark = { showContextMenu = false },
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

    if (showApktoolDecode && apktoolTarget != null) {
        ApktoolDecodeDialog(
            file = apktoolTarget!!,
            onDismiss = { showApktoolDecode = false },
            onOpenSettings = {
                showApktoolDecode = false
                showApktoolSettings = true
            },
        )
    }

    if (showApktoolBuild && apktoolTarget != null) {
        ApktoolBuildDialog(
            project = apktoolTarget!!,
            onDismiss = { showApktoolBuild = false },
            onOpenSettings = {
                showApktoolBuild = false
                showApktoolSettings = true
            },
        )
    }

    if (showApktoolSettings) ApktoolSettingsDialog(onDismiss = { showApktoolSettings = false })
    if (showApktoolCli) ApktoolCliDialog(onDismiss = { showApktoolCli = false })
    if (showApktoolJobs) {
        ApktoolJobsDialog(
            jobs = apktoolJobs,
            onCancel = apktoolJobsViewModel::cancel,
            onCancelAll = apktoolJobsViewModel::cancelAll,
            onDismiss = { showApktoolJobs = false },
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
                        onClose= {
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
                                    text = "Folder: ${activeState.folderCount} File: ${activeState.fileCount}" +
                                        if (isApktoolProject(File(activeState.currentPath))) " • Apktool project" else "",
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
                                    onClick = { showMainMenu = true },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        Icons.Default.MoreVert,
                                        contentDescription = "More Options",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                DropdownMenu(
                                    expanded = showMainMenu,
                                    onDismissRequest = { showMainMenu = false },
                                ) {
                                    if (isApktoolProject(File(activeState.currentPath))) {
                                        DropdownMenuItem(
                                            text = { Text("APK kompilieren (Projekt)") },
                                            onClick = {
                                                showMainMenu = false
                                                apktoolTarget = File(activeState.currentPath)
                                                showApktoolBuild = true
                                            },
                                        )
                                    }
                                    DropdownMenuItem(
                                        text = { Text("Apktool Jobs${if (activeApktoolJobCount > 0) " ($activeApktoolJobCount)" else ""}") },
                                        onClick = { showMainMenu = false; showApktoolJobs = true },
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Apktool Settings") },
                                        onClick = { showMainMenu = false; showApktoolSettings = true },
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Apktool CLI") },
                                        onClick = { showMainMenu = false; showApktoolCli = true },
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
                                    item.isDirectory -> viewModel.loadDirectory(
                                        ActivePane.LEFT,
                                        item.path
                                    )

                                    isApkLike(File(item.path)) -> {
                                        apktoolTarget = File(item.path)
                                        showApktoolDecode = true
                                    }

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
                                    item.isDirectory -> viewModel.loadDirectory(
                                        ActivePane.RIGHT,
                                        item.path
                                    )

                                    isApkLike(File(item.path)) -> {
                                        apktoolTarget = File(item.path)
                                        showApktoolDecode = true
                                    }

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