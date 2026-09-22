package io.github.lootdev78.mtapktool.feature.explorer.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import io.github.lootdev78.mtapktool.core.theme.MtClassicTopBar
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageViewerScreen(
    filePath: String,
    onBackClick: () -> Unit
) {
    val currentFile = remember { File(filePath) }
    val parentDir = remember { currentFile.parentFile }
    val imageFiles = remember {
        parentDir?.listFiles { file ->
            val ext = file.extension.lowercase()
            ext in setOf("jpg", "jpeg", "png", "gif", "webp", "bmp")
        }?.sortedBy { it.name } ?: listOf(currentFile)
    }
    
    val initialIndex = remember {
        val index = imageFiles.indexOfFirst { it.absolutePath == currentFile.absolutePath }
        if (index != -1) index else 0
    }

    val pagerState = rememberPagerState(initialPage = initialIndex) {
        imageFiles.size
    }

    Scaffold(
        topBar = {
            MtClassicTopBar(
                title = { Text(imageFiles[pagerState.currentPage].name, maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                containerColor = Color.Black.copy(alpha = 0.72f),
                contentColor = Color.White,
            )
        },
        containerColor = Color.Black
    ) { innerPadding ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            beyondViewportPageCount = 1,
            userScrollEnabled = true // We will manage this per-page if needed, or keep it simple for now
        ) { page ->
            var isZoomed by remember { mutableStateOf(false) }
            
            // Note: In a more advanced implementation, we'd pass isZoomed back to disable pager scrolling
            ZoomableImage(
                file = imageFiles[page],
                onZoomChanged = { zoomed -> isZoomed = zoomed }
            )
        }
    }
}

@Composable
fun ZoomableImage(
    file: File,
    onZoomChanged: (Boolean) -> Unit = {}
) {
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val state = rememberTransformableState { zoomChange, offsetChange, _, _ ->
        val newScale = (scale * zoomChange).coerceIn(1f, 5f)
        scale = newScale
        onZoomChanged(newScale > 1f)
        
        if (newScale > 1f) {
            offset = offset + offsetChange
        } else {
            offset = Offset.Zero
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .transformable(state = state)
            .graphicsLayer(
                scaleX = scale,
                scaleY = scale,
                translationX = offset.x,
                translationY = offset.y
            ),
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = file,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit
        )
    }
}
