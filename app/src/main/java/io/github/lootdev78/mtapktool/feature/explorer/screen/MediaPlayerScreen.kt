package io.github.lootdev78.mtapktool.feature.explorer.screen

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import java.io.File

@Composable
fun MediaPlayerScreen(source: String, displayName: String, video: Boolean, onBack: () -> Unit) {
    val context = LocalContext.current
    val queue = remember(source, video) { buildMediaQueue(source, video) }
    val initial = remember(queue, source) { queue.indexOf(source).coerceAtLeast(0) }
    val player = remember(queue, initial) {
        ExoPlayer.Builder(context).build().apply {
            val items = queue.map { path ->
                val uri = if (path.startsWith("content://") || path.startsWith("file://")) Uri.parse(path) else Uri.fromFile(File(path))
                MediaItem.fromUri(uri)
            }
            setMediaItems(items, initial, 0L)
            prepare()
            playWhenReady = true
        }
    }
    var currentName by remember { mutableStateOf(displayName) }
    DisposableEffect(player) {
        val listener = object : androidx.media3.common.Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val index = player.currentMediaItemIndex.coerceAtLeast(0)
                currentName = queue.getOrNull(index)?.let { if (it.startsWith("content://")) Uri.parse(it).lastPathSegment else File(it).name } ?: displayName
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener); player.release() }
    }
    BackHandler(onBack = onBack)

    Column(Modifier.fillMaxSize().background(if (video) Color.Black else MaterialTheme.colorScheme.background)) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = if (video) Color.Black else MaterialTheme.colorScheme.surfaceVariant,
            contentColor = if (video) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
            shadowElevation = 2.dp,
        ) {
            Row(Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                Text(currentName, Modifier.weight(1f).padding(start = 8.dp), fontSize = 18.sp, fontWeight = FontWeight.Medium, maxLines = 1)
            }
        }

        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            if (!video) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.AudioFile, null, modifier = Modifier.size(72.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(12.dp))
                    Text(currentName, maxLines = 1)
                    Spacer(Modifier.height(10.dp))
                    AndroidView(
                        factory = { ctx ->
                            PlayerView(ctx).apply { configurePlayerView(this, player, queue.size > 1) }
                        },
                        update = { view -> configurePlayerView(view, player, queue.size > 1) },
                        modifier = Modifier.fillMaxWidth().height(110.dp),
                    )
                }
            } else {
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply { configurePlayerView(this, player, queue.size > 1) }
                    },
                    update = { view -> configurePlayerView(view, player, queue.size > 1) },
                    modifier = Modifier.fillMaxSize().background(Color.Black),
                )
            }
        }
    }
}

private fun buildMediaQueue(source: String, video: Boolean): List<String> {
    if (source.startsWith("content://")) return listOf(source)
    val file = File(source)
    val extensions = if (video) setOf("mp4", "mkv", "webm", "avi", "mov", "m4v", "3gp", "ts") else setOf("mp3", "m4a", "aac", "ogg", "opus", "flac", "wav", "amr")
    return file.parentFile?.listFiles { candidate -> candidate.isFile && candidate.extension.lowercase() in extensions }
        ?.sortedBy { it.name.lowercase() }?.map(File::getAbsolutePath).orEmpty().ifEmpty { listOf(source) }
}

private fun configurePlayerView(view: PlayerView, player: ExoPlayer, showSkip: Boolean) {
    view.player = player
    view.useController = true
    // Media3 has changed these controller setters between releases. Reflection keeps
    // this screen source-compatible and avoids touching PlayerView's private fields.
    runCatching { view.javaClass.getMethod("setShowBuffering", Int::class.javaPrimitiveType).invoke(view, 1) }
    runCatching { view.javaClass.getMethod("setShowNextButton", Boolean::class.javaPrimitiveType).invoke(view, showSkip) }
    runCatching { view.javaClass.getMethod("setShowPreviousButton", Boolean::class.javaPrimitiveType).invoke(view, showSkip) }
}
