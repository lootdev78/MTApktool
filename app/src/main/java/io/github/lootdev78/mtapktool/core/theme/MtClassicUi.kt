package io.github.lootdev78.mtapktool.core.theme

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Flat 48dp chrome shared by restored MT-classic screens. */
@Composable
fun MtClassicTopBar(
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    containerColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    contentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = containerColor,
        contentColor = contentColor,
        shadowElevation = 2.dp,
    ) {
        CompositionLocalProvider(LocalContentColor provides contentColor) {
            Row(
                modifier = Modifier.fillMaxWidth().height(MtClassicMetrics.toolbarHeight).padding(horizontal = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                navigationIcon()
                ProvideTextStyle(
                    MaterialTheme.typography.titleMedium.copy(
                        fontSize = MtClassicMetrics.title,
                        fontWeight = FontWeight.Medium,
                        color = contentColor,
                    )
                ) {
                    androidx.compose.foundation.layout.Box(Modifier.weight(1f).padding(horizontal = 8.dp)) { title() }
                }
                actions()
            }
        }
    }
}

@Composable
fun MtClassicBottomBar(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        shadowElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(MtClassicMetrics.bottomBarHeight),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}
