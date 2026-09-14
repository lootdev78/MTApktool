package io.github.lootdev78.mtapktool.core.theme

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK
}

class ThemeManager(private val context: Context) {
    private val themeKey = stringPreferencesKey("app_theme_mode")

    val themeModeFlow: Flow<ThemeMode> = context.dataStore.data.map { preferences ->
        val savedTheme = preferences[themeKey] ?: ThemeMode.SYSTEM.name
        val mode = try {
            ThemeMode.valueOf(savedTheme)
        } catch (_: IllegalArgumentException) {
            ThemeMode.SYSTEM
        }
        writeLegacyThemeBridge(context, mode)
        mode
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { preferences ->
            preferences[themeKey] = mode.name
        }
        // AppCompat/XML modules need a synchronous value before their Activity theme is created.
        writeLegacyThemeBridge(context, mode)
    }

    companion object {
        private const val BRIDGE_PREFS = "mtapktool_theme_bridge"
        private const val BRIDGE_KEY = "mode"

        @JvmStatic
        fun bridgedMode(context: Context): ThemeMode {
            val raw = context.applicationContext
                .getSharedPreferences(BRIDGE_PREFS, Context.MODE_PRIVATE)
                .getString(BRIDGE_KEY, ThemeMode.SYSTEM.name)
                ?: ThemeMode.SYSTEM.name
            return runCatching { ThemeMode.valueOf(raw) }.getOrDefault(ThemeMode.SYSTEM)
        }

        private fun writeLegacyThemeBridge(context: Context, mode: ThemeMode) {
            context.applicationContext
                .getSharedPreferences(BRIDGE_PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(BRIDGE_KEY, mode.name)
                .apply()
        }
    }
}
