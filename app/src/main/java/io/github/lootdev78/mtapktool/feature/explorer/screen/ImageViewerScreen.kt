
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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

private val IMAGE_EXTENSIONS = setOf(
    "jpg",
    "jpeg",
    "png",
    "gif",
    "webp",
    "bmp"
)

@Composable
fun ImageViewerScreen(
    filePath: String,
    onBackClick: () -> Unit
) {
    val currentFile = remember(filePath) {
        File(filePath)
    }

    val parentDir = remember(currentFile) {
        currentFile.parentFile
    }

    val imageFiles = remember(currentFile, parentDir) {
        parentDir
            ?.listFiles { file ->
                file.isFile &&
                    file.extension.lowercase() in IMAGE_EXTENSIONS
            }
            ?.sortedBy { it.name.lowercase() }
            ?: listOf(currentFile)
    }

    val initialIndex = remember(currentFile, imageFiles) {
        val index = imageFiles.indexOfFirst {
            it.absolutePath == currentFile.absolutePath
        }

        if (index >= 0) index else 0
    }

    val pagerState = rememberPagerState(
        initialPage = initialIndex
    ) {
        imageFiles.size.coerceAtLeast(1)
    }

    Scaffold(
        topBar = {
            MtClassicTopBar(
                title = {
                    val currentPage = pagerState.currentPage
                        .coerceIn(
                            0,
                            imageFiles.lastIndex.coerceAtLeast(0)
                        )

                    Text(
                        text = imageFiles.getOrNull(currentPage)?.name
                            ?: currentFile.name,
                        maxLines = 1
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                containerColor = Color.Black.copy(alpha = 0.72f),
                contentColor = Color.White
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
            userScrollEnabled = true
        ) { page ->

            val imageFile = imageFiles.getOrNull(page)

            if (imageFile != null) {
                ZoomableImage(
                    file = imageFile
                )
            }
        }
    }
}

@Composable
fun ZoomableImage(
    file: File,
    onZoomChanged: (Boolean) -> Unit = {}
) {
    var scale by remember(file) {
        mutableStateOf(1f)
    }

    var offset by remember(file) {
        mutableStateOf(Offset.Zero)
    }

    val transformState = rememberTransformableState {
        panChange,
        zoomChange,
        _,
        _ ->

        val newScale = (scale * zoomChange)
            .coerceIn(1f, 5f)

        scale = newScale

        onZoomChanged(newScale > 1f)

        if (newScale > 1f) {
            offset += panChange
        } else {
            offset = Offset.Zero
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .transformable(
                state = transformState
            )
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = offset.x
                translationY = offset.y
            },
        contentAlignment = Alignment.Center
    ) {

        AsyncImage(
            model = file,
            contentDescription = file.name,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit
        )
    }
}
