
package io.github.lootdev78.mtapktool.feature.explorer.screen

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageViewerScreen(
    filePath: String,
    onBackClick: () -> Unit
) {
    val isContent = filePath.startsWith("content://")

    val imageSources = remember(filePath) {
        if (isContent) {
            listOf(filePath)
        } else {
            val file = File(filePath)

            file.parentFile
                ?.listFiles { candidate ->
                    candidate.isFile &&
                        candidate.extension.lowercase() in setOf(
                            "jpg",
                            "jpeg",
                            "png",
                            "gif",
                            "webp",
                            "bmp",
                            "heic",
                            "heif",
                            "avif"
                        )
                }
                ?.sortedBy { it.name.lowercase() }
                ?.map(File::getAbsolutePath)
                .orEmpty()
                .ifEmpty {
                    listOf(filePath)
                }
        }
    }

    val initialIndex = remember(imageSources, filePath) {
        imageSources.indexOf(filePath).coerceAtLeast(0)
    }

    val pagerState = rememberPagerState(
        initialPage = initialIndex,
        pageCount = { imageSources.size }
    )

    var chromeVisible by remember {
        mutableStateOf(true)
    }

    var zoomed by remember {
        mutableStateOf(false)
    }

    BackHandler(onBack = onBackClick)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            beyondViewportPageCount = 1,
            userScrollEnabled = !zoomed
        ) { page ->

            ZoomableImage(
                source = imageSources[page],
                onZoomChanged = { isZoomed ->
                    zoomed = isZoomed
                },
                onTap = {
                    chromeVisible = !chromeVisible
                }
            )
        }

        AnimatedVisibility(
            visible = chromeVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {

            TopAppBar(
                title = {

                    val currentSource =
                        imageSources[pagerState.currentPage]

                    val name =
                        if (currentSource.startsWith("content://")) {
                            Uri.parse(currentSource)
                                .lastPathSegment
                                ?: "Image"
                        } else {
                            File(currentSource).name
                        }

                    Text(
                        text = if (imageSources.size > 1) {
                            "$name  ${pagerState.currentPage + 1}/${imageSources.size}"
                        } else {
                            name
                        },
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

                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black.copy(alpha = 0.62f),
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        }
    }
}

@Composable
private fun ZoomableImage(
    source: String,
    onZoomChanged: (Boolean) -> Unit,
    onTap: () -> Unit
) {
    var scale by remember(source) {
        mutableFloatStateOf(1f)
    }

    var offset by remember(source) {
        mutableStateOf(Offset.Zero)
    }

    val transform = rememberTransformableState {
            zoomChange,
            offsetChange,
            _ ->

        val nextScale = (scale * zoomChange)
            .coerceIn(1f, 6f)

        scale = nextScale

        offset =
            if (nextScale > 1f) {
                offset + offsetChange
            } else {
                Offset.Zero
            }

        onZoomChanged(nextScale > 1f)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(source) {

                detectTapGestures(
                    onTap = {
                        onTap()
                    },

                    onDoubleTap = {

                        scale = if (scale > 1f) {
                            1f
                        } else {
                            2.5f
                        }

                        if (scale == 1f) {
                            offset = Offset.Zero
                        }

                        onZoomChanged(scale > 1f)
                    }
                )
            }
            .transformable(transform)
            .graphicsLayer(
                scaleX = scale,
                scaleY = scale,
                translationX = offset.x,
                translationY = offset.y
            ),

        contentAlignment = Alignment.Center
    ) {

        AsyncImage(
            model = if (source.startsWith("content://")) {
                Uri.parse(source)
            } else {
                File(source)
            },

            contentDescription = null,

            modifier = Modifier
                .fillMaxSize()
                .padding(2.dp),

            contentScale = ContentScale.Fit
        )
    }
}
