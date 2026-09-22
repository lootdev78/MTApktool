package io.github.lootdev78.mtapktool.feature.editor

import android.graphics.Typeface
import android.view.View
import android.widget.EditText
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import io.github.rosemoe.sora.event.ContentChangeEvent
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.EditorSearcher
import io.github.rosemoe.sora.widget.schemes.SchemeDarcula
import io.github.rosemoe.sora.widget.schemes.SchemeGitHub
import io.github.rosemoe.sora.widget.subscribeEvent

data class EditorSelectionRequest(val start: Int, val end: Int, val token: Long)

@Composable
fun CodeTextEditor(
    text: String,
    onTextChanged: (String) -> Unit,
    isWordWrap: Boolean = true,
    searchQuery: String = "",
    isRegex: Boolean = false,
    isMatchCase: Boolean = false,
    isDark: Boolean = false,
    selectionRequest: EditorSelectionRequest? = null,
    modifier: Modifier = Modifier,
) {
    var editorView by remember { mutableStateOf<View?>(null) }

    LaunchedEffect(isWordWrap, isDark, editorView) {
        when (val view = editorView) {
            is CodeEditor -> {
                view.isWordwrap = isWordWrap
                view.colorScheme = if (isDark) SchemeDarcula() else SchemeGitHub()
            }
            is EditText -> view.setHorizontallyScrolling(!isWordWrap)
        }
    }

    LaunchedEffect(searchQuery, isRegex, isMatchCase, editorView) {
        (editorView as? CodeEditor)?.let { editor ->
            if (searchQuery.isNotEmpty()) {
                runCatching { editor.searcher.search(searchQuery, EditorSearcher.SearchOptions(isRegex, isMatchCase)) }
            } else runCatching { editor.searcher.stopSearch() }
        }
    }

    LaunchedEffect(selectionRequest?.token, editorView) {
        selectionRequest?.let { request -> editorView?.let { applySelection(it, text, request) } }
    }

    AndroidView<View>(
        factory = { context ->
            runCatching<View> {
                CodeEditor(context).apply {
                    isWordwrap = isWordWrap
                    typefaceText = Typeface.MONOSPACE
                    setTextSize(14f)
                    isLineNumberEnabled = true
                    colorScheme = if (isDark) SchemeDarcula() else SchemeGitHub()
                    setText(text)
                    subscribeEvent<ContentChangeEvent> { _, _ -> onTextChanged(this.text.toString()) }
                    editorView = this
                }
            }.getOrElse {
                EditText(context).apply {
                    setText(text)
                    typeface = Typeface.MONOSPACE
                    textSize = 14f
                    setHorizontallyScrolling(!isWordWrap)
                    addTextChangedListener(object : android.text.TextWatcher {
                        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = onTextChanged(s?.toString().orEmpty())
                        override fun afterTextChanged(s: android.text.Editable?) = Unit
                    })
                    editorView = this
                }
            }
        },
        update = { view ->
            editorView = view
            when (view) {
                is CodeEditor -> {
                    view.isWordwrap = isWordWrap
                    view.colorScheme = if (isDark) SchemeDarcula() else SchemeGitHub()
                    if (view.text.toString() != text) view.setText(text)
                }
                is EditText -> {
                    view.setHorizontallyScrolling(!isWordWrap)
                    if (view.text.toString() != text) view.setText(text)
                }
            }
            selectionRequest?.let { applySelection(view, text, it) }
        },
        modifier = modifier.fillMaxSize(),
    )
}

private fun applySelection(view: View, text: String, request: EditorSelectionRequest) {
    val start = request.start.coerceIn(0, text.length)
    val end = request.end.coerceIn(start, text.length)
    if (view is EditText) {
        runCatching { view.requestFocus(); view.setSelection(start, end) }
        return
    }
    if (view !is CodeEditor) return
    val startLc = offsetToLineColumn(text, start)
    val endLc = offsetToLineColumn(text, end)
    runCatching {
        val region = view.javaClass.methods.firstOrNull {
            it.name == "setSelectionRegion" && it.parameterTypes.size == 4 && it.parameterTypes.all { t -> t == Int::class.javaPrimitiveType }
        }
        if (region != null) region.invoke(view, startLc.first, startLc.second, endLc.first, endLc.second)
        else view.javaClass.methods.firstOrNull {
            it.name == "setSelection" && it.parameterTypes.size == 2 && it.parameterTypes.all { t -> t == Int::class.javaPrimitiveType }
        }?.invoke(view, startLc.first, startLc.second)
        view.requestFocus()
    }
}

private fun offsetToLineColumn(text: String, offset: Int): Pair<Int, Int> {
    var line = 0
    var column = 0
    var i = 0
    val target = offset.coerceIn(0, text.length)
    while (i < target) {
        if (text[i] == '\n') { line++; column = 0 } else column++
        i++
    }
    return line to column
}
