package io.github.lootdev78.mtapktool.feature.explorer.component

import android.R
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties


@Composable
fun ConfirmDialog(
    title: String,
    subtitle: String?,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,

){

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false // Allows custom dialog sizing
        )
    ) {

        Surface(
            shape = RoundedCornerShape(2.dp),
            color = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            shadowElevation = 8.dp,
            modifier = Modifier.fillMaxWidth(0.88f),
        ) {
            Column(
                modifier = Modifier.padding(24.dp)
            ) {

                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                if (subtitle != null) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }


                Spacer(Modifier.height(24.dp))


                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {

                    TextButton(onClick = {
                        onDismiss()
                    }) {
                        Text("Cancel")
                    }

                    Spacer(Modifier.width(8.dp))

                    Button(onClick = {
                        onConfirm()
                    }) {
                        Text("OK")
                    }
                }
            }

        }

    }
}
