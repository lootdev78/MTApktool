package io.github.lootdev78.mtapktool

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import io.github.lootdev78.mtapktool.core.theme.MTExplorerTheme
import io.github.lootdev78.mtapktool.core.theme.ThemeManager
import io.github.lootdev78.mtapktool.core.theme.ThemeMode
import io.github.lootdev78.mtapktool.settings.ExplorerPreferences
import androidx.core.net.toUri

class MainActivity : ComponentActivity() {

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    private val storagePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions.entries.all { it.value }
        if (granted) {
            // Permission granted, you might want to trigger a refresh in the ViewModel
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestStoragePermission()
        requestNotificationPermission()
        ExternalOpenBridge.publish(intent)

        val themeManager = ThemeManager(applicationContext)
        ExplorerPreferences.init(applicationContext)

        setContent {
            val themeMode by themeManager.themeModeFlow.collectAsState(initial = ThemeMode.SYSTEM)
            val explorerPrefs by ExplorerPreferences.state.collectAsState()
            val systemDark = isSystemInDarkTheme()
            val darkBars = when (themeMode) {
                ThemeMode.SYSTEM -> systemDark
                ThemeMode.DARK -> true
                ThemeMode.LIGHT -> false
            }
            SideEffect {
                applicationContext.getSharedPreferences("mtapktool_theme_bridge", MODE_PRIVATE)
                    .edit()
                    .putString("mode", themeMode.name)
                    .apply()
                val statusDark = 0xFF121318.toInt()
                val navDark = 0xFF0A0B0F.toInt()
                val light = 0xFFF6F3F7.toInt()
                enableEdgeToEdge(
                    statusBarStyle = if (darkBars) {
                        SystemBarStyle.dark(statusDark)
                    } else {
                        SystemBarStyle.light(light, statusDark)
                    },
                    navigationBarStyle = if (darkBars) {
                        SystemBarStyle.dark(navDark)
                    } else {
                        SystemBarStyle.light(light, navDark)
                    },
                )
            }

            MTExplorerTheme(themeMode = themeMode, accentKey = explorerPrefs.accentKey) {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    contentWindowInsets = WindowInsets.safeDrawing,
                ) { innerPadding ->
                    Surface(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        val navController = rememberNavController()
                        // Directly launch MTExplorerApp as the primary composable view
                        MTExplorerApp(navController)
                    }
                }
            }
        }
    }



    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        ExternalOpenBridge.publish(intent)
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                try {
                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                        data = "package:$packageName".toUri()
                    }
                    startActivity(intent)
                } catch (e: Exception) {
                    val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                    startActivity(intent)
                }
            }
        } else {
            val permissions = arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
            val allGranted = permissions.all {
                checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED
            }
            if (!allGranted) {
                storagePermissionLauncher.launch(permissions)
            }
        }
    }
}