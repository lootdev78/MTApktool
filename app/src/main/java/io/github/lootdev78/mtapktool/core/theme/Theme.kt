package io.github.lootdev78.mtapktool.core.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
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
    surfaceContainerLowest = Color(0xFF202020),
    surfaceContainerLow = Color(0xFF28282A),
    surfaceContainer = Color(0xFF303030),
    surfaceContainerHigh = Color(0xFF36383A),
    surfaceContainerHighest = Color(0xFF3F3F3F),
    outline = Color(0xFF555555),
    outlineVariant = Color(0xFF505050),
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
    onSurfaceVariant = MdThemeLightOnSurfaceVariant,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFAFAFA),
    surfaceContainer = Color(0xFFF5F5F5),
    surfaceContainerHigh = Color(0xFFF0F0F0),
    surfaceContainerHighest = Color(0xFFE8E8E8),
    outline = Color(0xFFD0D0D0),
    outlineVariant = Color(0xFFE0E0E0),
    scrim = Color.Black,
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
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val context = LocalContext.current
    val useMonet = accentKey.startsWith("monet") && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val base = when {
        (dynamicColor || useMonet) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> DarkColorScheme
        else -> LightColorScheme
    }
    val accent = if (useMonet) base.primary else accentForKey(accentKey, dark)
    val onAccent = if (accent.luminance() > 0.55f) Color(0xFF111318) else Color.White
    val scheme = base.copy(
        primary = accent,
        onPrimary = onAccent,
        primaryContainer = accent.copy(alpha = if (dark) 0.34f else 0.16f),
        secondary = accent,
        tertiary = accent,
    )
    MaterialTheme(colorScheme = scheme, typography = Typography, shapes = MtClassicShapes, content = content)
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
    else -> if (dark) Color(0xFF1976D2) else Color(0xFF42A5F5)
}
