package io.github.lootdev78.mtapktool.core.theme

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * Compact dialog shell used by the reconstructed MT-classic flows.
 * It intentionally avoids Material3's large default radius/paddings.
 */
@Composable
fun MtClassicAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
    shape: Shape = RoundedCornerShape(2.dp),
    containerColor: Color = MaterialTheme.colorScheme.surface,
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = modifier.fillMaxWidth(MtClassicMetrics.dialogWidthFraction).widthIn(max = 460.dp),
            shape = shape,
            color = containerColor,
            contentColor = MaterialTheme.colorScheme.onSurface,
            shadowElevation = 8.dp,
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(
                    start = MtClassicMetrics.dialogHorizontalPadding,
                    top = MtClassicMetrics.dialogTopPadding,
                    end = MtClassicMetrics.dialogHorizontalPadding,
                    bottom = MtClassicMetrics.dialogBottomPadding,
                ),
            ) {
                if (title != null) {
                    ProvideTextStyle(
                        MaterialTheme.typography.titleLarge.copy(
                            fontSize = MtClassicMetrics.title,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    ) { title() }
                }
                if (title != null && text != null) Spacer(Modifier.height(12.dp))
                if (text != null) {
                    ProvideTextStyle(
                        MaterialTheme.typography.bodyMedium.copy(
                            fontSize = MtClassicMetrics.body,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    ) { text() }
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().height(MtClassicMetrics.dialogActionHeight),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (dismissButton != null) dismissButton()
                    confirmButton()
                }
            }
        }
    }
}
