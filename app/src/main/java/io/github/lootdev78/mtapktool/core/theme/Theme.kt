package io.github.lootdev78.mtapktool.core.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

private val DarkColorScheme = darkColorScheme(
    primary = MdThemeDarkPrimary,
    onPrimary = MdThemeDarkOnPrimary,
    primaryContainer = MdThemeDarkPrimaryContainer,
    onPrimaryContainer = MdThemeDarkOnPrimaryContainer,
    secondary = MdThemeDarkSecondary,
    onSecondary = MdThemeDarkOnSecondary,
    secondaryContainer = MdThemeDarkSecondaryContainer,
    onSecondaryContainer = MdThemeDarkOnSecondaryContainer,
    background = MdThemeDarkBackground,
    onBackground = MdThemeDarkOnSurface,
    surface = MdThemeDarkSurface,
    onSurface = MdThemeDarkOnSurface,
    surfaceVariant = MdThemeDarkSurfaceVariant,
    onSurfaceVariant = MdThemeDarkOnSurfaceVariant,
    surfaceContainerLowest = Color(0xFF0F0F0F),
    surfaceContainerLow = Color(0xFF242424),
    surfaceContainer = Color(0xFF303030),
    surfaceContainerHigh = Color(0xFF484848),
    surfaceContainerHighest = Color(0xFF505050),
    outline = Color(0xFF666666),
    outlineVariant = Color(0xFF3D3D3D),
    scrim = Color.Black,
)

private val LightColorScheme = lightColorScheme(
    primary = MdThemeLightPrimary,
    onPrimary = MdThemeLightOnPrimary,
    primaryContainer = MdThemeLightPrimaryContainer,
    onPrimaryContainer = MdThemeLightOnPrimaryContainer,
    secondary = MdThemeLightSecondary,
    onSecondary = MdThemeLightOnSecondary,
    secondaryContainer = MdThemeLightSecondaryContainer,
    onSecondaryContainer = MdThemeLightOnSecondaryContainer,
    background = MdThemeLightBackground,
    onBackground = MdThemeLightOnSurface,
    surface = MdThemeLightSurface,
    onSurface = MdThemeLightOnSurface,
    surfaceVariant = MdThemeLightSurfaceVariant,
    onSurfaceVariant = MdThemeLightOnSurfaceVariant
)


private val MtClassicShapes = Shapes(
    extraSmall = RoundedCornerShape(0.dp),
    small = RoundedCornerShape(0.dp),
    medium = RoundedCornerShape(2.dp),
    large = RoundedCornerShape(2.dp),
    extraLarge = RoundedCornerShape(2.dp),
)
@Composable
fun MTExplorerTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = false,
    accentKey: String = "blue",
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    val context = LocalContext.current
    val useMonet = accentKey.startsWith("monet") && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val baseScheme = when {
        (dynamicColor || useMonet) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }
    val accent = if (useMonet) baseScheme.primary else accentForKey(accentKey, darkTheme)
    val onAccent = if (accent.luminance() > 0.55f) Color(0xFF111318) else Color.White
    val colorScheme = baseScheme.copy(
        primary = accent,
        onPrimary = onAccent,
        secondary = accent,
    )

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = MtClassicShapes,
        content = content
    )
}


private fun accentForKey(key: String, dark: Boolean): Color = when (key) {
    "black" -> if (dark) Color(0xFFBDBDBD) else Color(0xFF202124)
    "true_black" -> if (dark) Color(0xFFE0E0E0) else Color.Black
    "gray" -> if (dark) Color(0xFFB8B8B8) else Color(0xFF555555)
    "cyan" -> if (dark) Color(0xFF66C7E8) else Color(0xFF0D7BA8)
    "indigo" -> if (dark) Color(0xFFAEB9FF) else Color(0xFF3949AB)
    "brown" -> if (dark) Color(0xFFD8A291) else Color(0xFFA26754)
    "pink" -> if (dark) Color(0xFFF29AB6) else Color(0xFFA94769)
    "purple" -> if (dark) Color(0xFFC5AEF7) else Color(0xFF5C4695)
    "lime" -> if (dark) Color(0xFFB7D98B) else Color(0xFF5A8238)
    "green" -> if (dark) Color(0xFF85D58B) else Color(0xFF266C2D)
    "teal_dark" -> if (dark) Color(0xFF6BD4C7) else Color(0xFF00695C)
    else -> if (dark) Color(0xFF2196F3) else Color(0xFF1976C8)
}
