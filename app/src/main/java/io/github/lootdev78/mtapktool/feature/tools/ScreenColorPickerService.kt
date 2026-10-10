package io.github.lootdev78.mtapktool.feature.tools

import android.app.*
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.*
import android.provider.Settings
import android.view.*
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import io.github.lootdev78.mtapktool.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ColorCaptureState(val running: Boolean = false, val argb: Int = Color.rgb(33, 150, 243), val sampled: Boolean = false, val error: String? = null) {
    val hex: String get() = String.format("#%06X", argb and 0xffffff)
    val rgb: String get() = "RGB(${Color.red(argb)}, ${Color.green(argb)}, ${Color.blue(argb)})"
}

/** Reads only the selected pixel; screenshots are neither stored nor transmitted. */
class ScreenColorPickerService : Service() {
    private var taskId: String? = null
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var windows: WindowManager
    private var projection: MediaProjection? = null
    private var display: VirtualDisplay? = null
    private var reader: ImageReader? = null
    private var target: View? = null
    private var panel: View? = null
    private var label: TextView? = null
    private var width = 0; private var height = 0
    private var pendingX = 0; private var pendingY = 0; private var sampleAfter = 0L
    private val restoreOverlay = Runnable { sampleAfter = 0; target?.visibility = View.VISIBLE; panel?.visibility = View.VISIBLE }
    private val callback = object : MediaProjection.Callback() {
        override fun onStop() { stopSelf() }
        override fun onCapturedContentResize(newWidth: Int, newHeight: Int) {
            val metrics = screenSize()
            if (newWidth != metrics.first || newHeight != metrics.second) {
                fail("Für die Pipette den gesamten Bildschirm freigeben")
                return
            }
            resize(newWidth, newHeight)
        }
    }
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == STOP) { stopSelf(); return START_NOT_STICKY }
        if (projection != null) return START_NOT_STICKY
        if (intent == null || !Settings.canDrawOverlays(this)) { stopSelf(); return START_NOT_STICKY }
        windows = getSystemService(WindowManager::class.java)
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "Bildschirm-Pipette", NotificationManager.IMPORTANCE_LOW))
        val stop = PendingIntent.getService(this, 71, Intent(this, ScreenColorPickerService::class.java).setAction(STOP), PendingIntent.FLAG_IMMUTABLE)
        startForeground(ID, NotificationCompat.Builder(this, CHANNEL).setSmallIcon(R.drawable.mt_ic_tools)
            .setContentTitle("MTApktool • Color Picker").setContentText("Pipette ziehen und loslassen").setOngoing(true).addAction(0, "Stoppen", stop).build())
        try {
            taskId = io.github.lootdev78.mtapktool.tasks.ToolTaskRegistry.begin("Bildschirm-Pipette", "Pipette ziehen und loslassen") { handler.post { stopSelf() } }
            @Suppress("DEPRECATION") val consent = intent.getParcelableExtra<Intent>("consent") ?: error("Bildschirmfreigabe fehlt")
            projection = getSystemService(MediaProjectionManager::class.java).getMediaProjection(intent.getIntExtra("result", Activity.RESULT_CANCELED), consent)
            projection!!.registerCallback(callback, handler)
            val size = screenSize(); width = size.first; height = size.second
            reader = newReader(width, height)
            display = projection!!.createVirtualDisplay("MTApktoolColorPicker", width, height, resources.displayMetrics.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, reader!!.surface, null, handler)
            mutableState.value = mutableState.value.copy(running = true, sampled = false, error = null)
            showOverlay()
        } catch (error: Throwable) { fail(error.message ?: "Color Picker konnte nicht starten") }
        return START_NOT_STICKY
    }
    @Suppress("DEPRECATION")
    private fun screenSize(): Pair<Int, Int> {
        if (Build.VERSION.SDK_INT >= 30) return windows.maximumWindowMetrics.bounds.let { it.width() to it.height() }
        val metrics = android.util.DisplayMetrics(); windows.defaultDisplay.getRealMetrics(metrics); return metrics.widthPixels to metrics.heightPixels
    }
    private fun newReader(w: Int, h: Int): ImageReader = ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, 2).apply {
        setOnImageAvailableListener({ source ->
            val image = source.acquireLatestImage() ?: return@setOnImageAvailableListener
            try {
                if (sampleAfter == 0L || SystemClock.uptimeMillis() < sampleAfter) return@setOnImageAvailableListener
                val x = pendingX.coerceIn(0, image.width - 1); val y = pendingY.coerceIn(0, image.height - 1)
                val plane = image.planes[0]; val offset = y * plane.rowStride + x * plane.pixelStride
                val buffer = plane.buffer
                if (offset + 3 >= buffer.limit()) return@setOnImageAvailableListener
                val argb = Color.rgb(buffer.get(offset).toInt() and 255, buffer.get(offset + 1).toInt() and 255, buffer.get(offset + 2).toInt() and 255)
                mutableState.value = ColorCaptureState(true, argb, true)
                taskId?.let { io.github.lootdev78.mtapktool.tasks.ToolTaskRegistry.progress(it, message = mutableState.value.hex) }
                label?.text = "${mutableState.value.hex}\n${mutableState.value.rgb}"
                label?.setBackgroundColor(argb)
                label?.setTextColor(if (Color.red(argb) * .299 + Color.green(argb) * .587 + Color.blue(argb) * .114 > 140) Color.BLACK else Color.WHITE)
                handler.removeCallbacks(restoreOverlay); restoreOverlay.run()
            } finally { image.close() }
        }, handler)
    }
    private fun resize(w: Int, h: Int) {
        if (w <= 0 || h <= 0 || w == width && h == height || display == null) return
        val old = reader; val replacement = newReader(w, h)
        display?.resize(w, h, resources.displayMetrics.densityDpi)
        display?.surface = replacement.surface
        reader = replacement; width = w; height = h; old?.close()
    }
    override fun onConfigurationChanged(newConfig: Configuration) { super.onConfigurationChanged(newConfig); if (::windows.isInitialized) screenSize().let { resize(it.first, it.second) } }
    private fun showOverlay() {
        val palette = OverlayPalette.read(this)
        val size = (44 * resources.displayMetrics.density).toInt()
        val params = WindowManager.LayoutParams(size, size, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, PixelFormat.TRANSLUCENT)
        params.gravity = Gravity.TOP or Gravity.LEFT; params.x = width / 2 - size / 2; params.y = height / 2 - size / 2
        val crosshair = object : View(this) {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 4f }
            override fun onDraw(canvas: Canvas) {
                paint.color = Color.BLACK; canvas.drawCircle(size / 2f, size / 2f, size / 3f, paint)
                paint.color = Color.WHITE; paint.strokeWidth = 2f; canvas.drawCircle(size / 2f, size / 2f, size / 3f, paint)
                canvas.drawLine(size / 2f, 0f, size / 2f, size.toFloat(), paint); canvas.drawLine(0f, size / 2f, size.toFloat(), size / 2f, paint)
            }
            override fun performClick(): Boolean { super.performClick(); return true }
        }
        crosshair.contentDescription = "Pipette ziehen und loslassen"
        var downX = 0f; var downY = 0f; var startX = 0; var startY = 0
        crosshair.setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> { downX = event.rawX; downY = event.rawY; startX = params.x; startY = params.y }
                MotionEvent.ACTION_MOVE -> {
                    params.x = (startX + event.rawX - downX).toInt().coerceIn(-size / 2, width - size / 2)
                    params.y = (startY + event.rawY - downY).toInt().coerceIn(-size / 2, height - size / 2)
                    windows.updateViewLayout(view, params)
                }
                MotionEvent.ACTION_UP -> {
                    pendingX = params.x + size / 2; pendingY = params.y + size / 2
                    target?.visibility = View.INVISIBLE; panel?.visibility = View.INVISIBLE
                    // Wait for a frame without the picker overlay, preventing its own pixels being sampled.
                    sampleAfter = SystemClock.uptimeMillis() + 140
                    handler.removeCallbacks(restoreOverlay); handler.postDelayed(restoreOverlay, 1200)
                    view.performClick()
                }
                MotionEvent.ACTION_CANCEL -> restoreOverlay.run()
            }
            true
        }
        windows.addView(crosshair, params); target = crosshair
        val toolbar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; background = palette.background() }
        label = palette.label(this, "Pipette ziehen\nund loslassen").also { toolbar.addView(it) }
        toolbar.addView(palette.label(this, "Kopieren") {
            getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Farbe", mutableState.value.hex))
            Toast.makeText(this, "${mutableState.value.hex} kopiert", Toast.LENGTH_SHORT).show()
        })
        toolbar.addView(palette.label(this, "× Stoppen") { stopSelf() })
        val toolbarParams = WindowManager.LayoutParams(-2, -2, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT).apply { gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL; y = 48 }
        windows.addView(toolbar, toolbarParams); panel = toolbar
    }
    private fun fail(message: String) { taskId?.let { io.github.lootdev78.mtapktool.tasks.ToolTaskRegistry.finish(it, io.github.lootdev78.mtapktool.tasks.ToolTaskStatus.FAILED, message) }; mutableState.value = mutableState.value.copy(error = message); Toast.makeText(this, message, Toast.LENGTH_LONG).show(); stopSelf() }
    override fun onDestroy() {
        taskId?.let { io.github.lootdev78.mtapktool.tasks.ToolTaskRegistry.finish(it, message = "Pipette beendet: ${mutableState.value.hex}") }; taskId = null
        handler.removeCallbacksAndMessages(null)
        target?.let { runCatching { windows.removeView(it) } }; panel?.let { runCatching { windows.removeView(it) } }
        target = null; panel = null
        display?.release(); display = null
        reader?.close(); reader = null
        projection?.unregisterCallback(callback); projection?.stop(); projection = null
        mutableState.value = mutableState.value.copy(running = false)
        stopForeground(STOP_FOREGROUND_REMOVE); super.onDestroy()
    }
    companion object {
        private const val STOP = "mt.color.STOP"
        private const val CHANNEL = "mt_screen_color"
        private const val ID = 3144
        private val mutableState = MutableStateFlow(ColorCaptureState())
        val state = mutableState.asStateFlow()
        fun start(context: Context, result: Int, consent: Intent) { ContextCompat.startForegroundService(context, Intent(context, ScreenColorPickerService::class.java).putExtra("result", result).putExtra("consent", consent)) }
        fun stop(context: Context) { context.stopService(Intent(context, ScreenColorPickerService::class.java)) }
    }
}
