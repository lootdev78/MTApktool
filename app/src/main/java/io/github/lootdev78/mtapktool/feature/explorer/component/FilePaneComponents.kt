package io.github.lootdev78.mtapktool.feature.explorer.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.PlayArrow
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import io.github.lootdev78.mtapktool.core.theme.*
import io.github.lootdev78.mtapktool.feature.explorer.model.FileItem
import io.github.lootdev78.mtapktool.feature.explorer.state.*
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun ClassicFilePane(
    paneState: PaneState,
    isActive: Boolean,
    onFocus: () -> Unit,
    onNavigateUp: () -> Unit,
    onRefresh: () -> Unit,
    onItemClick: (FileItem) -> Unit,
    onItemLongClick: (FileItem) -> Unit,
    onSwipeSelect: (FileItem) -> Unit,
    onBuildProject: (File) -> Unit = {},
    modifier: Modifier = Modifier
) {


    val listState = rememberLazyListState()
    val pullToRefreshState = rememberPullToRefreshState()


    // Highlight border for active pane (optional visual clue)
    val paneBgColor = if (isActive) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.background
    val elevation = if (isActive) 2.dp else 0.dp

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
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary, strokeWidth = 2.dp)
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
                        // 1. Static Parent Directory Item "."
                        if (paneState.searchQuery.isEmpty()) {
                            item {
                                ParentDirectoryRow(onClick = {
                                    onFocus()
                                    onNavigateUp()
                                })
                            }
                            val currentProject = File(paneState.currentPath)
                            if (File(currentProject, "apktool.yml").isFile) {
                                item(key = "__mtapktool_build__${paneState.currentPath}") {
                                    ApktoolProjectBuildRow(
                                        project = currentProject,
                                        onClick = {
                                            onFocus()
                                            onBuildProject(currentProject)
                                        },
                                    )
                                }
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
                                isSelected = paneState.selectedPaths.contains(item.path) || isHighlighted,
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
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                                    Color.Transparent
                                )
                            )
                        )
                )
            }
        }
    }
}

@Composable
private fun ApktoolProjectBuildRow(project: File, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(ColorApk.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = ColorApk, modifier = Modifier.size(25.dp))
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "Dieses Projekt kompilieren",
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                "${project.name} • apktool.yml",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ParentDirectoryRow(onClick: () -> Unit) {

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
            .padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Dark Squircle Icon
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(ColorFolder.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector =  Icons.Default.Folder,
                contentDescription = null,
                tint = ColorFolder,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = "..",
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ClassicFileRow(
    item: FileItem,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onSwipeSelect: (FileItem) -> Unit
) {
    var isPressed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // Tracks horizontal swipe offset for visual feedback
    val offsetX = remember { Animatable(0f) }
    var hasTriggeredSwipe by remember { mutableStateOf(false) }

    // Dynamic background color state
    val targetBackgroundColor = when {
        isPressed -> MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
        isSelected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
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
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val isApktoolProject = item.isDirectory && File(item.path, "apktool.yml").isFile
        val (icon, iconColor) = when {
            isApktoolProject ->
                Icons.Default.Build to ColorApk

            item.isDirectory ->
                Icons.Default.Folder to ColorFolder

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
                .size(32.dp)
                .clip(RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (item.isImageFile()) {
                AsyncImage(
                    model = item.path,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(iconColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconColor,
                        modifier = Modifier.size(20.dp)
                    )
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
                fontSize = 14.sp,
                lineHeight = 15.sp,
                style = LocalTextStyle.current.copy(
                    platformStyle = PlatformTextStyle(includeFontPadding = false)
                ),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = item.formattedDate,
                    fontSize = 11.sp,
                    lineHeight = 12.sp,
                    style = LocalTextStyle.current.copy(
                        platformStyle = PlatformTextStyle(includeFontPadding = false)
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )

                if (isApktoolProject) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Apktool project",
                        fontSize = 11.sp,
                        lineHeight = 12.sp,
                        style = LocalTextStyle.current.copy(
                            platformStyle = PlatformTextStyle(includeFontPadding = false)
                        ),
                        color = MaterialTheme.colorScheme.primary
                    )
                } else if (!item.isDirectory && item.sizeText.isNotEmpty()) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = item.sizeText,
                        fontSize = 11.sp,
                        lineHeight = 12.sp,
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