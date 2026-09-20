package io.github.lootdev78.mtapktool.feature.explorer.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import io.github.lootdev78.mtapktool.feature.explorer.viewmodel.FileConflictAction
import io.github.lootdev78.mtapktool.feature.explorer.viewmodel.FileConflictRequest

/** Classic MT-style transfer conflict prompt. */
@Composable
fun FileConflictDialog(
    request: FileConflictRequest,
    onResolve: (FileConflictAction, Boolean) -> Unit,
) {
    var applyAll by remember(request.id) { mutableStateOf(false) }
    Dialog(onDismissRequest = { onResolve(FileConflictAction.CANCEL, false) }) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(2.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 0.dp,
        ) {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 18.dp)) {
                Text("File already exist", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    request.name,
                    modifier = Modifier.padding(top = 16.dp),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    request.destination,
                    modifier = Modifier.padding(top = 4.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )

                Row(
                    Modifier.fillMaxWidth().padding(top = 12.dp).clickable { applyAll = !applyAll },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = applyAll, onCheckedChange = { applyAll = it })
                    Spacer(Modifier.width(4.dp))
                    Text("Apply to all")
                }

                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { onResolve(FileConflictAction.CANCEL, false) }) { Text("CANCEL") }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { onResolve(FileConflictAction.SKIP, applyAll) }) { Text("SKIP") }
                    TextButton(onClick = { onResolve(FileConflictAction.KEEP_BOTH, applyAll) }) { Text("KEEP BOTH") }
                    TextButton(onClick = { onResolve(FileConflictAction.OVERWRITE, applyAll) }) { Text("OVERWRITE") }
                }
            }
        }
    }
}
