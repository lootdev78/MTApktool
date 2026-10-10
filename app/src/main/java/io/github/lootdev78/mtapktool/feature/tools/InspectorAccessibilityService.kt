package io.github.lootdev78.mtapktool.feature.tools

import android.accessibilityservice.AccessibilityService
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs

data class InspectedNode(
    val path: String, val depth: Int, val bounds: Rect, val className: String, val resourceId: String,
    val packageName: String, val text: String, val description: String, val children: Int,
    val visible: Boolean, val enabled: Boolean, val clickable: Boolean, val focused: Boolean,
    val selected: Boolean, val checked: Boolean, val password: Boolean,
) {
    fun details() = "$className\nID: ${resourceId.ifBlank { "—" }}\nPaket: $packageName\nPfad: $path\nBounds: ${bounds.flattenToString()}\nText: ${if (password) "[Passwort ausgeblendet]" else text}\nBeschreibung: $description\nKinder: $children\nSichtbar=$visible Aktiv=$enabled Klickbar=$clickable Fokus=$focused Ausgewählt=$selected Geprüft=$checked"
}

object InspectorState {
    val running = MutableStateFlow(false)
    val nodes = MutableStateFlow<List<InspectedNode>>(emptyList())
    val activity = MutableStateFlow("")
    fun json(): String {
        val array = JSONArray()
        nodes.value.forEach { node -> array.put(JSONObject().put("path", node.path).put("depth", node.depth).put("class", node.className)
            .put("id", node.resourceId).put("package", node.packageName).put("bounds", node.bounds.flattenToString())
            .put("text", node.text).put("description", node.description).put("visible", node.visible).put("enabled", node.enabled)
            .put("clickable", node.clickable).put("focused", node.focused).put("selected", node.selected).put("checked", node.checked).put("password", node.password)) }
        return JSONObject().put("activity", activity.value).put("capturedAt", System.currentTimeMillis()).put("nodes", array).toString(2)
    }
}

/** On-demand, read-only inspection of the accessibility hierarchy. */
class InspectorAccessibilityService : AccessibilityService() {
    private var taskId: String? = null
    private lateinit var manager: WindowManager
    private var floating: View? = null
    private var picker: View? = null
    override fun onServiceConnected() { super.onServiceConnected(); instance = this; manager = getSystemService(WindowManager::class.java) }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (InspectorState.running.value && event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && event.packageName != packageName) {
            InspectorState.activity.value = "${event.packageName}/${event.className}"
        }
    }
    override fun onInterrupt() { stopInspector() }

    @Suppress("DEPRECATION")
    fun capture(): Boolean {
        val root = windows.firstOrNull { it.type == AccessibilityWindowInfo.TYPE_APPLICATION && it.isActive }?.root
            ?: windows.firstOrNull { it.type == AccessibilityWindowInfo.TYPE_APPLICATION && it.isFocused }?.root
            ?: rootInActiveWindow ?: return false
        val result = mutableListOf<InspectedNode>()
        fun walk(node: AccessibilityNodeInfo, path: String, depth: Int) {
            if (depth > 80 || result.size >= 5000) return
            val bounds = Rect(); node.getBoundsInScreen(bounds)
            val password = node.isPassword
            result += InspectedNode(path, depth, bounds, node.className?.toString().orEmpty(), node.viewIdResourceName.orEmpty(),
                node.packageName?.toString().orEmpty(), if (password) "" else node.text?.toString().orEmpty().take(2000),
                if (password) "" else node.contentDescription?.toString().orEmpty().take(2000), node.childCount,
                node.isVisibleToUser, node.isEnabled, node.isClickable, node.isFocused, node.isSelected, node.isChecked, password)
            for (index in 0 until node.childCount) {
                val child = node.getChild(index) ?: continue
                try { walk(child, "$path/$index", depth + 1) } finally { child.recycle() }
            }
        }
        try { walk(root, "0", 0) } finally { root.recycle() }
        InspectorState.nodes.value = result
        taskId?.let { io.github.lootdev78.mtapktool.tasks.ToolTaskRegistry.progress(it, message = "${result.size} Layout-Elemente") }
        return result.isNotEmpty()
    }

    fun startInspector() {
        if (floating != null) return
        taskId = io.github.lootdev78.mtapktool.tasks.ToolTaskRegistry.begin("Layout Inspector", "Layout-Fenster aktiv") { android.os.Handler(mainLooper).post { stopInspector() } }
        InspectorState.running.value = true
        val palette = OverlayPalette.read(this)
        val button = palette.label(this, "▣ Layout")
        button.background = palette.background()
        val params = WindowManager.LayoutParams(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT)
        params.gravity = Gravity.TOP or Gravity.LEFT; params.x = 16; params.y = 180
        var downX = 0f; var downY = 0f; var startX = 0; var startY = 0
        button.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> { downX = event.rawX; downY = event.rawY; startX = params.x; startY = params.y }
                MotionEvent.ACTION_MOVE -> { params.x = startX + (event.rawX - downX).toInt(); params.y = startY + (event.rawY - downY).toInt(); manager.updateViewLayout(button, params) }
                MotionEvent.ACTION_UP -> if (abs(event.rawX - downX) < 12 && abs(event.rawY - downY) < 12) showPicker()
            }
            true
        }
        try { manager.addView(button, params); floating = button }
        catch (error: Throwable) { InspectorState.running.value = false; Toast.makeText(this, error.message, Toast.LENGTH_LONG).show() }
    }

    private fun showPicker() {
        if (!capture()) { Toast.makeText(this, "Keine zugängliche Oberfläche gefunden", Toast.LENGTH_SHORT).show(); return }
        if (picker != null) return
        floating?.visibility = View.GONE
        val palette = OverlayPalette.read(this)
        val container = FrameLayout(this)
        val details = palette.label(this, "Element antippen • Rechteck wählen")
        val outlines = object : View(this) {
            var selected: InspectedNode? = null
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = palette.accent; style = Paint.Style.STROKE; strokeWidth = 2f }
            override fun onDraw(canvas: Canvas) {
                super.onDraw(canvas)
                InspectorState.nodes.value.filter { it.visible && !it.bounds.isEmpty }.forEach { paint.alpha = 90; canvas.drawRect(it.bounds, paint) }
                selected?.let { paint.alpha = 255; paint.strokeWidth = 5f; canvas.drawRect(it.bounds, paint); paint.strokeWidth = 2f }
            }
            override fun onTouchEvent(event: MotionEvent): Boolean {
                if (event.action == MotionEvent.ACTION_UP) {
                    selected = InspectorState.nodes.value.filter { it.visible && it.bounds.contains(event.rawX.toInt(), event.rawY.toInt()) }.maxByOrNull { it.depth }
                    details.text = selected?.details() ?: "Kein zugängliches Element an dieser Position"
                    invalidate(); performClick()
                }
                return true
            }
            override fun performClick(): Boolean { super.performClick(); return true }
        }
        container.addView(outlines, FrameLayout.LayoutParams(-1, -1))
        val panel = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; background = palette.background() }
        panel.addView(details, LinearLayout.LayoutParams(-1, -2))
        val actions = LinearLayout(this)
        actions.addView(palette.label(this, "Kopieren") { getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Layout", details.text)) })
        actions.addView(palette.label(this, "Baum") { hidePicker(); startActivity(ToolsActivity.intent(this, "inspector").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) })
        actions.addView(palette.label(this, "Zurück") { hidePicker() })
        actions.addView(palette.label(this, "Stoppen") { stopInspector() })
        panel.addView(actions)
        container.addView(panel, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))
        val params = WindowManager.LayoutParams(-1, -1, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN, PixelFormat.TRANSLUCENT)
        try { manager.addView(container, params); picker = container }
        catch (error: Throwable) { floating?.visibility = View.VISIBLE; Toast.makeText(this, error.message, Toast.LENGTH_LONG).show() }
    }
    private fun hidePicker() { picker?.let { runCatching { manager.removeView(it) } }; picker = null; floating?.visibility = View.VISIBLE }
    fun stopInspector() { taskId?.let { io.github.lootdev78.mtapktool.tasks.ToolTaskRegistry.finish(it, message = "Inspector beendet") }; taskId = null; hidePicker(); floating?.let { runCatching { manager.removeView(it) } }; floating = null; InspectorState.running.value = false }
    override fun onDestroy() { stopInspector(); if (instance === this) instance = null; super.onDestroy() }
    companion object { @Volatile var instance: InspectorAccessibilityService? = null; private set }
}
