package io.github.lootdev78.mtapktool.feature.explorer.screen

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import java.io.File

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ImageViewerScreen(filePath: String, onBackClick: () -> Unit) {
    val currentFile = remember(filePath) { File(filePath) }
    val imageFiles = remember(filePath) {
        currentFile.parentFile?.listFiles { file ->
            file.isFile && file.extension.lowercase() in setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "heif", "avif")
        }?.sortedBy { it.name.lowercase() }.orEmpty().ifEmpty { listOf(currentFile) }
    }
    val initialIndex = remember(imageFiles, filePath) { imageFiles.indexOfFirst { it.absolutePath == currentFile.absolutePath }.coerceAtLeast(0) }
    val pagerState = rememberPagerState(initialPage = initialIndex, pageCount = { imageFiles.size })
    var chromeVisible by remember { mutableStateOf(true) }
    var zoomed by remember { mutableStateOf(false) }
    BackHandler(onBack = onBackClick)

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            beyondViewportPageCount = 1,
            userScrollEnabled = !zoomed,
        ) { page ->
            ZoomableImage(
                file = imageFiles[page],
                onZoomChanged = { zoomed = it },
                onTap = { chromeVisible = !chromeVisible },
            )
        }

        if (chromeVisible) {
            Surface(
                modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth(),
                color = Color.Black.copy(alpha = 0.72f),
                contentColor = Color.White,
                shadowElevation = 2.dp,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onBackClick) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White) }
                    Text(
                        text = imageFiles[pagerState.currentPage].name + if (imageFiles.size > 1) "  ${pagerState.currentPage + 1}/${imageFiles.size}" else "",
                        modifier = Modifier.weight(1f).padding(start = 8.dp),
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun ZoomableImage(file: File, onZoomChanged: (Boolean) -> Unit, onTap: () -> Unit) {
    var scale by remember(file) { mutableFloatStateOf(1f) }
    var offset by remember(file) { mutableStateOf(Offset.Zero) }
    val state = rememberTransformableState { zoomChange, offsetChange, _ ->
        val next = (scale * zoomChange).coerceIn(1f, 6f)
        scale = next
        offset = if (next > 1f) offset + offsetChange else Offset.Zero
        onZoomChanged(next > 1f)
    }
    Box(
        modifier = Modifier.fillMaxSize()
            .pointerInput(file.absolutePath) {
                detectTapGestures(
                    onTap = { onTap() },
                    onDoubleTap = {
                        scale = if (scale > 1f) 1f else 2.5f
                        if (scale == 1f) offset = Offset.Zero
                        onZoomChanged(scale > 1f)
                    },
                )
            }
            .transformable(state)
            .graphicsLayer(scaleX = scale, scaleY = scale, translationX = offset.x, translationY = offset.y),
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(model = file, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
    }
}
