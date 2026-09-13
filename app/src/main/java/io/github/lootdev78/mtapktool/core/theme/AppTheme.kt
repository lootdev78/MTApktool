package io.github.lootdev78.mtapktool.core.theme

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// DataStore Extension
private val Context.dataStore by preferencesDataStore(name = "settings")

enum class ThemeMode {
    SYSTEM, // Follow System Settings (Automatic)
    LIGHT,  // Force Light Theme
    DARK    // Force Dark Theme
}

class ThemeManager(private val context: Context) {
    private val themeKey = stringPreferencesKey("app_theme_mode")

    val themeModeFlow: Flow<ThemeMode> = context.dataStore.data.map { preferences ->
        val savedTheme = preferences[themeKey] ?: ThemeMode.SYSTEM.name
        try {
            ThemeMode.valueOf(savedTheme)
        } catch (e: IllegalArgumentException) {
            ThemeMode.SYSTEM
        }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { preferences ->
            preferences[themeKey] = mode.name
        }
    }
}