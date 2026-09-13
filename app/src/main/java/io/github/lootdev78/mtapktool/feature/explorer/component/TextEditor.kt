package io.github.lootdev78.mtapktool.feature.editor

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.component.EditorAutoCompletion
import io.github.rosemoe.sora.text.Content
import io.github.rosemoe.sora.langs.textmate.TextMateLanguage
import io.github.rosemoe.sora.widget.subscribeEvent
import io.github.rosemoe.sora.widget.schemes.SchemeDarcula
import io.github.rosemoe.sora.widget.schemes.SchemeGitHub

@Composable
fun CodeTextEditor(
    text: String,
    onTextChanged: (String) -> Unit,
    isWordWrap: Boolean = true,
    searchQuery: String = "",
    isRegex: Boolean = false,
    isMatchCase: Boolean = false,
    isDark: Boolean = false,
    modifier: Modifier = Modifier
) {
    var editorInstance by remember { mutableStateOf<CodeEditor?>(null) }

    // Update Word Wrap when state changes
    LaunchedEffect(isWordWrap, isDark) {
        editorInstance?.isWordwrap = isWordWrap
        editorInstance?.colorScheme = if (isDark) SchemeDarcula() else SchemeGitHub()
    }

    // Handle Search / Find Matches
    LaunchedEffect(searchQuery, isRegex, isMatchCase) {
        editorInstance?.let { editor ->
            if (searchQuery.isNotEmpty()) {
                editor.searcher.search(
                    searchQuery,
                    io.github.rosemoe.sora.widget.EditorSearcher.SearchOptions(
                        isRegex,
                        isMatchCase
                    )
                )
            } else {
                editor.searcher.stopSearch()
            }
        }
    }

    AndroidView(
        factory = { context ->
            CodeEditor(context).apply {
                // UI & Behavior Configuration
                this.isWordwrap = isWordWrap
                this.typefaceText = android.graphics.Typeface.MONOSPACE
                this.setTextSize(14f)

                // Show Line Numbers
                this.isLineNumberEnabled = true
                this.colorScheme = if (isDark) SchemeDarcula() else SchemeGitHub()

                // Initial Content Setup
                this.setText(text)

                // Text Change Listener
                this.subscribeEvent<io.github.rosemoe.sora.event.ContentChangeEvent> { _, _ ->
                    onTextChanged(this.text.toString())
                }

                editorInstance = this
            }
        },
        update = { editor ->
            // Keep editor synced if external state updates
            if (editor.text.toString() != text) {
                editor.setText(text)
            }
        },
        modifier = modifier.fillMaxSize()
    )
}