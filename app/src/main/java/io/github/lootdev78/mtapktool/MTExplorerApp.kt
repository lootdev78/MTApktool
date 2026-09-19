package io.github.lootdev78.mtapktool

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import io.github.lootdev78.mtapktool.feature.editor.CodeEditorScreen
import io.github.lootdev78.mtapktool.feature.explorer.screen.ExplorerScreen
import io.github.lootdev78.mtapktool.feature.explorer.screen.ApkExtractorScreen
import io.github.lootdev78.mtapktool.feature.explorer.screen.ImageViewerScreen
import io.github.lootdev78.mtapktool.feature.explorer.screen.MediaPlayerScreen
import io.github.lootdev78.mtapktool.feature.explorer.screen.Screen

@Composable
fun MTExplorerApp(navController: NavHostController) {
    NavHost(
        navController = navController,
        startDestination = Screen.Explorer.route
    ) {
        composable(Screen.Explorer.route) {
            ExplorerScreen(navController)
        }
        composable(Screen.ApkExtractor.route) {
            ApkExtractorScreen(onBack = { navController.popBackStack() })
        }

        composable(
            route = Screen.Editor.route,
            arguments = listOf(
                navArgument("filePath") { defaultValue = "" },
                navArgument("fileName") { defaultValue = "" }
            )
        ) { backStackEntry ->
            val filePath = backStackEntry.arguments?.getString("filePath") ?: ""
            val fileName = backStackEntry.arguments?.getString("fileName") ?: ""

            CodeEditorScreen(
                filePath = filePath,
                fileName = fileName,
                onBackClick = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.MediaPlayer.route,
            arguments = listOf(
                navArgument("source") { defaultValue = "" },
                navArgument("fileName") { defaultValue = "" },
                navArgument("video") { defaultValue = "false" },
            )
        ) { backStackEntry ->
            MediaPlayerScreen(
                source = backStackEntry.arguments?.getString("source") ?: "",
                displayName = backStackEntry.arguments?.getString("fileName") ?: "",
                video = backStackEntry.arguments?.getString("video").toBoolean(),
                onBack = { navController.popBackStack() },
            )
        }

        composable(
            route = Screen.ImageViewer.route,
            arguments = listOf(
                navArgument("filePath") { defaultValue = "" },
                navArgument("fileName") { defaultValue = "" }
            )
        ) { backStackEntry ->
            val filePath = backStackEntry.arguments?.getString("filePath") ?: ""

            ImageViewerScreen(
                filePath = filePath,
                onBackClick = { navController.popBackStack() }
            )
        }
    }
}