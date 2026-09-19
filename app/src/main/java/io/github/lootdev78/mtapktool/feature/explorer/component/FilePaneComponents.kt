package io.github.lootdev78.mtapktool.feature.explorer.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import io.github.lootdev78.mtapktool.R
import io.github.lootdev78.mtapktool.core.theme.*
import io.github.lootdev78.mtapktool.feature.explorer.model.FileItem
import io.github.lootdev78.mtapktool.feature.explorer.state.*
import io.github.lootdev78.mtapktool.feature.explorer.util.ApkArchiveReader
import io.github.lootdev78.mtapktool.settings.ExplorerPreferences
import io.github.lootdev78.mtapktool.settings.ExplorerPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ClassicFilePane(
    paneState: PaneState,
    isActive: Boolean,
    onFocus: () -> Unit,
    onNavigateUp: () -> Unit,
    onRefresh: () -> Unit,
    onPathClick: () -> Unit,
    onItemClick: (FileItem) -> Unit,
    onItemLongClick: (FileItem) -> Unit,
    onSwipeSelect: (FileItem) -> Unit,
    onBuildProject: (File) -> Unit = {},
    modifier: Modifier = Modifier
) {


    val context = LocalContext.current
    ExplorerPreferences.init(context)
    val explorerPrefs by ExplorerPreferences.state.collectAsState()
    val listState = rememberLazyListState()
    val pullToRefreshState = rememberPullToRefreshState()


    // Highlight border for active pane (optional visual clue)
    val paneBgColor by animateColorAsState(
        targetValue = if (isActive) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.background,
        animationSpec = tween(140),
        label = "paneBackground",
    )
    val elevation by animateDpAsState(
        targetValue = if (isActive) 2.dp else 0.dp,
        animationSpec = tween(140),
        label = "paneElevation",
    )

    LaunchedEffect(paneState.highlightedItemName) {
        val targetIndex = paneState.items.indexOfFirst { it.name == paneState.highlightedItemName }
        if (targetIndex != -1) {
            listState.animateScrollToItem(targetIndex)
        }
    }

    // White card panel container with soft drop shadow
    Surface(
        modifier = modifier
            .fillMaxHeight()
            .shadow(elevation = elevation, shape = RoundedCornerShape(0.dp))
            // Focus on pointer-down before a child row consumes the gesture. This keeps the
            // single path/header at the top synchronized with whichever pane the user touches.
            .pointerInput(onFocus) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        if (event.changes.any { it.pressed && !it.previousPressed }) onFocus()
                    }
                }
            }
            // Clicking ANY empty area in the pane focuses it
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onFocus
            ),
        color = paneBgColor
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (paneState.isLoading) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary, strokeWidth = 2.dp)
                        Spacer(Modifier.height(12.dp))
                        paneState.loadingLabel?.let { label ->
                            Text(
                                label,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 13.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        paneState.loadingProgress?.let { progress ->
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "${progress.coerceIn(0, 100)}%",
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
            } else {
                PullToRefreshBox(
                    isRefreshing = paneState.isLoading,
                    onRefresh = {
                        onFocus()
                        onRefresh()
                    },
                    state = pullToRefreshState,
                    modifier = Modifier.fillMaxSize()
                ) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 12.dp, top = 2.dp),
                    ) {
                        // Project build action belongs at the very top and uses the same row metrics as files.
                        if (paneState.searchQuery.isEmpty()) {
                            val currentProject = if (paneState.currentPath.startsWith("content://")) null else File(paneState.currentPath)
                            if (currentProject != null && File(currentProject, "apktool.yml").isFile) {
                                item(key = "__mtapktool_build__${paneState.currentPath}") {
                                    ApktoolProjectBuildRow(
                                        project = currentProject,
                                        prefs = explorerPrefs,
                                        onClick = { onFocus(); onBuildProject(currentProject) },
                                    )
                                }
                            }
                            item {
                                ParentDirectoryRow(prefs = explorerPrefs, onClick = {
                                    onFocus()
                                    onNavigateUp()
                                })
                            }
                        }

                        // 2. File & Directory Items
                        items(
                            items = paneState.filteredItems,
                            key = { it.path }
                        ) { item ->
                            val isHighlighted = item.name == paneState.highlightedItemName
                            ClassicFileRow(
                                item = item,
                                prefs = explorerPrefs,
                                isSelected = paneState.selectedPaths.contains(item.path),
                                isRecentlyChanged = item.path in paneState.recentlyChangedPaths,
                                onClick = {
                                    onFocus()
                                    onItemClick(item)
                                },
                                onLongClick = {
                                    onFocus()
                                    onItemLongClick(item)

                                },
                                onSwipeSelect = { onSwipeSelect(item) }
                            )
                        }
                    }
                }
            }
            if (isActive) {
                // MT-style active pane cue: a subtle inner shade on all four edges.
                // It is deliberately neutral rather than a bright outline so the last-used
                // pane reads as "pressed inward", matching the reference dual-pane UI.
                val shade = Color.Black.copy(alpha = 0.16f)
                Box(
                    Modifier.fillMaxWidth().height(5.dp).align(Alignment.TopCenter)
                        .background(Brush.verticalGradient(listOf(shade, Color.Transparent)))
                )
                Box(
                    Modifier.fillMaxWidth().height(5.dp).align(Alignment.BottomCenter)
                        .background(Brush.verticalGradient(listOf(Color.Transparent, shade)))
                )
                Box(
                    Modifier.fillMaxHeight().width(5.dp).align(Alignment.CenterStart)
                        .background(Brush.horizontalGradient(listOf(shade, Color.Transparent)))
                )
                Box(
                    Modifier.fillMaxHeight().width(5.dp).align(Alignment.CenterEnd)
                        .background(Brush.horizontalGradient(listOf(Color.Transparent, shade)))
                )
            }
        }
    }
}

@Composable
private fun ApktoolProjectBuildRow(project: File, prefs: ExplorerPrefs, onClick: () -> Unit) {
    val metrics = fileListMetrics(prefs.fileListSize)
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 6.dp, vertical = metrics.verticalPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(metrics.iconSize).clip(RoundedCornerShape(10.dp)).background(ColorApk.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = ColorApk, modifier = Modifier.size(metrics.innerIconSize))
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text("Dieses Projekt kompilieren", fontSize = metrics.nameSize, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${project.name} • apktool.yml", fontSize = metrics.detailSize, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun ParentDirectoryRow(prefs: ExplorerPrefs, onClick: () -> Unit) {
    val metrics = fileListMetrics(prefs.fileListSize)
    var isPressed by remember { mutableStateOf(false) }

    val animatedBgColor by animateColorAsState(
        targetValue = if (isPressed) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else Color.Transparent,
        animationSpec = tween(durationMillis = if (isPressed) 0 else 120),
        label = "parent_tap_effect"
    )


    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(animatedBgColor)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        isPressed = true
                        try {
                            awaitRelease()
                        } finally {
                            isPressed = false
                        }
                    },
                    onTap = { onClick() }
                )
            }
            .padding(horizontal = 6.dp, vertical = metrics.verticalPadding),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Dark Squircle Icon
        Box(
            modifier = Modifier
                .size(metrics.iconSize)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(R.drawable.mt_ic_folder),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.size(metrics.innerIconSize)
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = "..",
            fontSize = metrics.nameSize,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ClassicFileRow(
    item: FileItem,
    prefs: ExplorerPrefs,
    isSelected: Boolean,
    isRecentlyChanged: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onSwipeSelect: (FileItem) -> Unit
) {
    var isPressed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val metrics = fileListMetrics(prefs.fileListSize)
    val apkIcon by produceState<android.graphics.Bitmap?>(
        initialValue = null,
        key1 = item.path,
        key2 = item.modifiedAt,
    ) {
        value = if (!item.isSaf && !item.isDirectory && item.extensionName == "apk") {
            withContext(Dispatchers.IO) { ApkArchiveReader.icon(context, item.file) }
        } else {
            null
        }
    }

    // Tracks horizontal swipe offset for visual feedback
    val offsetX = remember { Animatable(0f) }
    var hasTriggeredSwipe by remember { mutableStateOf(false) }

    // Dynamic background color state
    val targetBackgroundColor = when {
        isPressed -> MaterialTheme.colorScheme.primary.copy(alpha = 0.24f)
        isSelected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
        else -> Color.Transparent
    }

    val animatedBgColor by animateColorAsState(
        targetValue = targetBackgroundColor,
        animationSpec = tween(durationMillis = if (isPressed) 0 else 120),
        label = "file_row_bg"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            // 1. Move row visually during swipe
            .graphicsLayer {
                translationX = offsetX.value
            }
            .background(animatedBgColor)
            // 2. Gesture handling: Horizontal Drag (Swipe)
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = {
                        hasTriggeredSwipe = false
                    },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        scope.launch {
                            // 1. Allow symmetric drag both Left (-180f) and Right (+180f)
                            val newOffset = (offsetX.value + dragAmount).coerceIn(-180f, 180f)
                            offsetX.snapTo(newOffset)

                            // 2. Trigger selection when dragging past threshold in EITHER direction (-60f or +60f)
                            if (kotlin.math.abs(offsetX.value) > 60f && !hasTriggeredSwipe) {
                                hasTriggeredSwipe = true
                                onSwipeSelect(item)
                            }
                        }
                    },
                    onDragEnd = {
                        scope.launch {
                            offsetX.animateTo(
                                targetValue = 0f,
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                    stiffness = Spring.StiffnessLow
                                )
                            )
                        }
                    },
                    onDragCancel = {
                        scope.launch {
                            offsetX.animateTo(0f)
                        }
                    }
                )
            }
            // 3. Gesture handling: Tap & Long Click
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        // Only trigger tap highlight if row is not currently being swiped
                        if (offsetX.value == 0f) {
                            isPressed = true
                            try {
                                awaitRelease()
                            } finally {
                                isPressed = false
                            }
                        }
                    },
                    onTap = { onClick() },
                    onLongPress = { onLongClick() }
                )
            }
            .padding(horizontal = 6.dp, vertical = metrics.verticalPadding),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val isApktoolProject = item.isDirectory && File(item.path, "apktool.yml").isFile
        val (icon, iconColor) = when {
            isApktoolProject -> Icons.Default.Build to ColorApk
            item.isDirectory -> Icons.Default.Folder to MaterialTheme.colorScheme.tertiary

            item.isApkFile() ->
                Icons.Default.Android to ColorApk

            item.isArchiveFile() ->
                Icons.Default.FolderZip to ColorArchive

            item.isPdfFile() ->
                Icons.Default.PictureAsPdf to ColorPdf

            item.isVideoFile() ->
                Icons.Default.VideoFile to ColorMedia

            item.isAudioFile() ->
                Icons.Default.AudioFile to ColorMedia

            item.isWebFile() ->
                Icons.Default.Code to ColorWeb

            item.isEditableTextFile() ->
                Icons.Default.Code to ColorDocument

            else ->
                Icons.Default.Description to ColorDocument
        }

        // Dark Rounded Icon Container
        Box(
            modifier = Modifier
                .size(metrics.iconSize)
                .clip(RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (item.isImageFile() && (!item.isSaf || prefs.loadExternalThumbnails)) {
                AsyncImage(
                    model = item.path,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else if (apkIcon != null) {
                Image(
                    bitmap = apkIcon!!.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(if (item.isDirectory) MaterialTheme.colorScheme.surfaceContainerHigh else iconColor.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center
                ) {
                    if (item.isDirectory && !isApktoolProject) {
                        Icon(
                            painter = painterResource(R.drawable.mt_ic_folder),
                            contentDescription = null,
                            tint = iconColor,
                            modifier = Modifier.size(metrics.innerIconSize),
                        )
                    } else {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = iconColor,
                            modifier = Modifier.size(metrics.innerIconSize)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Text Column
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            Text(
                text = item.name,
                fontSize = metrics.nameSize,
                lineHeight = metrics.nameLineHeight,
                style = LocalTextStyle.current.copy(
                    platformStyle = PlatformTextStyle(includeFontPadding = false)
                ),
                color = if (isRecentlyChanged) Color(0xFF2CBF4A) else MaterialTheme.colorScheme.onSurface,
                maxLines = prefs.maxFileNameLines.coerceIn(1, 8),
                overflow = TextOverflow.Ellipsis
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = formatItemDate(item.modifiedAt, prefs),
                    fontSize = metrics.detailSize,
                    lineHeight = metrics.detailLineHeight,
                    style = LocalTextStyle.current.copy(
                        platformStyle = PlatformTextStyle(includeFontPadding = false)
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )

                if (!item.isDirectory && item.sizeText.isNotEmpty()) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = item.sizeText,
                        fontSize = metrics.detailSize,
                        lineHeight = metrics.detailLineHeight,
                        style = LocalTextStyle.current.copy(
                            platformStyle = PlatformTextStyle(includeFontPadding = false)
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}

private data class FileListMetrics(
    val iconSize: androidx.compose.ui.unit.Dp,
    val innerIconSize: androidx.compose.ui.unit.Dp,
    val verticalPadding: androidx.compose.ui.unit.Dp,
    val nameSize: androidx.compose.ui.unit.TextUnit,
    val nameLineHeight: androidx.compose.ui.unit.TextUnit,
    val detailSize: androidx.compose.ui.unit.TextUnit,
    val detailLineHeight: androidx.compose.ui.unit.TextUnit,
)

private fun fileListMetrics(size: String): FileListMetrics = when (size) {
    "big" -> FileListMetrics(46.dp, 28.dp, 8.dp, 18.sp, 20.sp, 13.sp, 15.sp)
    "medium" -> FileListMetrics(39.dp, 24.dp, 6.dp, 16.sp, 18.sp, 12.sp, 14.sp)
    else -> FileListMetrics(32.dp, 20.dp, 4.dp, 14.sp, 15.sp, 11.sp, 12.sp)
}

private fun formatItemDate(modifiedAt: Long, prefs: ExplorerPrefs): String {
    if (modifiedAt <= 0L) return ""
    val configured = prefs.dateTimeFormat.ifBlank { "dd-MM-yyyy HH:mm:ss" }
    val pattern = if (prefs.fileListTimePreference == "hide_seconds_simplified_year") {
        configured.replace(":ss", "").replace("yyyy", "yy")
    } else configured
    return runCatching { SimpleDateFormat(pattern, Locale.getDefault()).format(Date(modifiedAt)) }
        .getOrElse { SimpleDateFormat("dd-MM-yy HH:mm", Locale.getDefault()).format(Date(modifiedAt)) }
}
