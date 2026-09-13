package io.github.lootdev78.mtapktool.feature.explorer.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.lootdev78.mtapktool.feature.explorer.model.StorageInfo
import io.github.lootdev78.mtapktool.feature.explorer.model.formatSize
import io.github.lootdev78.mtapktool.feature.explorer.model.getStorageRoots
import io.github.lootdev78.mtapktool.feature.explorer.viewmodel.ExplorerViewModel
import java.io.File


@Composable
fun SideBar(
    drawerWidth: Dp,
    viewModel: ExplorerViewModel = viewModel(),
    bookmarks: List<String> = emptyList(),
    onBookmarkClick: (String) -> Unit = {},
    onRemoveBookmark: (String) -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onClose: () -> Unit
) {
    val context = LocalContext.current

    val activePane by viewModel.activePane.collectAsState()

    val storageRoots by produceState<List<StorageInfo>>(
        initialValue = emptyList(),
        key1 = context
    ) {
        value = getStorageRoots(context)
    }

    ModalDrawerSheet(
        modifier = Modifier
            .fillMaxHeight()
            .width(drawerWidth)
    ) {
        Text(
            text = "MT Explorer",
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.titleLarge
        )

        HorizontalDivider()

        StorageList(
            storages = storageRoots,
            onStorageClick = { storage ->
                onClose()
                viewModel.navigateToDirectPath(
                    activePane,
                    storage.path
                )
            }
        )

        HorizontalDivider()

        if (bookmarks.isNotEmpty()) {
            Text(
                text = "Bookmarks",
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            bookmarks.forEach { path ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onClose()
                            onBookmarkClick(path)
                        }
                        .padding(start = 12.dp, top = 2.dp, bottom = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Bookmark,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = File(path).name.ifBlank { path },
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontSize = 12.sp
                    )
                    IconButton(onClick = { onRemoveBookmark(path) }) {
                        Icon(Icons.Default.Close, contentDescription = "Remove bookmark")
                    }
                }
            }
            HorizontalDivider()
        }

        NavigationDrawerItem(
            label = {
                Text("Settings")
            },
            selected = false,
            onClick = {
                onClose()
                onOpenSettings()
            },
            icon = {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = null
                )
            }
        )
    }
}



@Composable
fun StorageList(
    storages: List<StorageInfo>,
    onStorageClick: (StorageInfo) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = "Local",
            modifier = Modifier.padding(
                horizontal = 10.dp,
                vertical = 3.dp
            ),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        HorizontalDivider()

        storages.forEach { storage ->
            StorageItem(
                storage = storage,
                onClick = {
                    onStorageClick(storage)
                }
            )
        }
    }
}

@Composable
fun StorageItem(
    storage: StorageInfo,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(
                horizontal = 6.dp,
                vertical = 2.dp
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.onSurface),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = storage.icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.surface,
                modifier = Modifier.size(22.dp)
            )
        }

        Spacer(Modifier.width(6.dp))

        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = storage.name,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface
            )

//            Text(
//                text = storage.path,
//                fontSize = 11.sp,
//                color = MaterialTheme.colorScheme.onSurfaceVariant
//            )
            Spacer(Modifier.height(3.dp))
            LinearProgressIndicator(
                progress = { storage.usedPercentage },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
            )

            Spacer(Modifier.height(3.dp))

            Text(
                text = "${formatSize(storage.usedSpace)} used, " +
                        "${formatSize(storage.freeSpace)} available",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}