package io.github.lootdev78.mtapktool.feature.explorer.screen

import java.net.URLEncoder

sealed class Screen(val route: String) {
    object Explorer : Screen("explorer")

    object ImageViewer : Screen("imageViewer/{filePath}/{fileName}") {
        fun createRoute(filePath: String, fileName: String): String {
            val encodedPath = URLEncoder.encode(filePath, "UTF-8")
            val encodedName = URLEncoder.encode(fileName, "UTF-8")
            return "imageViewer/$encodedPath/$encodedName"
        }
    }
}
