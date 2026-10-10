package io.github.lootdev78.mtapktool.settings

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.lootdev78.mtapktool.core.theme.MTExplorerTheme
import io.github.lootdev78.mtapktool.core.theme.ThemeManager
import io.github.lootdev78.mtapktool.core.theme.ThemeMode

class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        ExplorerPreferences.init(this)
        val manager = ThemeManager(this)
        val initial = runCatching { SettingsPage.valueOf(intent.getStringExtra("page") ?: "ROOT") }.getOrDefault(SettingsPage.ROOT)
        setContent {
            val mode by manager.themeModeFlow.collectAsState(initial = ThemeMode.SYSTEM)
            val prefs by ExplorerPreferences.state.collectAsState()
            val systemDark = isSystemInDarkTheme()
            val darkBars = when (mode) {
                ThemeMode.SYSTEM -> systemDark
                ThemeMode.DARK -> true
                ThemeMode.LIGHT -> false
            }
            MTExplorerTheme(themeMode = mode, accentKey = prefs.accentKey) {
                val colors = androidx.compose.material3.MaterialTheme.colorScheme
                SideEffect {
                    getSharedPreferences("mtapktool_theme_bridge", MODE_PRIVATE).edit()
                        .putString("mode", mode.name).putInt("primary", colors.primary.toArgb())
                        .putInt("on_primary", colors.onPrimary.toArgb()).putInt("surface", colors.surface.toArgb())
                        .putInt("on_surface", colors.onSurface.toArgb()).putInt("navigation", colors.surfaceVariant.toArgb())
                        .putInt("container", colors.primaryContainer.toArgb()).putInt("on_container", colors.onPrimaryContainer.toArgb()).apply()
                    val status = colors.surface.toArgb()
                    val navigation = colors.surfaceVariant.toArgb()
                    enableEdgeToEdge(
                        statusBarStyle = if (darkBars) SystemBarStyle.dark(status) else SystemBarStyle.light(status, status),
                        navigationBarStyle = if (darkBars) SystemBarStyle.dark(navigation) else SystemBarStyle.light(navigation, navigation),
                    )
                }
                Surface(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                    AppSettingsScreen(onDismiss = { finish() }, initialPage = initial)
                }
            }
        }
    }
    companion object {
        fun intent(context: Context, page: SettingsPage = SettingsPage.ROOT) =
            Intent(context, SettingsActivity::class.java).putExtra("page", page.name)
    }
}

@Composable
fun SettingsPageContainer(asDialog: Boolean, onDismiss: () -> Unit, content: @Composable () -> Unit) {
    if (asDialog) Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = true)) { content() }
    else content()
}
