# Keep your app's models from being obfuscated/stripped
-keep class io.github.lootdev78.mtapktool.feature.explorer.model.** { *; }
-keep class io.github.lootdev78.mtapktool.feature.explorer.state.** { *; }

# Keep Compose reflection components safe
-keepclassmembers class * extends androidx.compose.ui.node.LayoutNode { *; }