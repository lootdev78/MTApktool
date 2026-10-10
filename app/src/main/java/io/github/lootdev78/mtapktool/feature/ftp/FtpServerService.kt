package io.github.lootdev78.mtapktool.feature.ftp

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import io.github.lootdev78.mtftp.MtFtpServer
import io.github.lootdev78.mtapktool.MainActivity
import io.github.lootdev78.mtapktool.R
import io.github.lootdev78.mtapktool.tasks.ToolTaskRegistry
import io.github.lootdev78.mtapktool.tasks.ToolTaskStatus
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.net.NetworkInterface

data class FtpServerState(val running: Boolean = false, val starting: Boolean = false, val addresses: List<String> = emptyList(), val folder: String = "", val error: String? = null)

class FtpServerService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var server: MtFtpServer? = null
    private var taskId: String? = null
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == STOP) { stopSelf(); return START_NOT_STICKY }
        if (intent == null) { stopSelf(); return START_NOT_STICKY }
        if (server != null || mutableState.value.starting) return START_NOT_STICKY
        val folder = intent.getStringExtra("folder").orEmpty()
        val port = intent.getIntExtra("port", 2121)
        val username = intent.getStringExtra("user").orEmpty()
        val password = intent.getStringExtra("password").orEmpty()
        val writable = intent.getBooleanExtra("writable", false)
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "FTP-Server", NotificationManager.IMPORTANCE_LOW))
        startForeground(NOTIFICATION, notification("Wird gestartet …"))
        mutableState.value = FtpServerState(starting = true, folder = folder)
        taskId = ToolTaskRegistry.begin("FTP-Server", folder) { stopSelf() }
        scope.launch {
            val candidate = MtFtpServer()
            try {
                candidate.start(File(folder), port, username, password, writable)
                ensureActive()
                server = candidate
                val addresses = lanAddresses().map { "ftp://$it:$port/" }
                mutableState.value = FtpServerState(running = true, addresses = addresses, folder = folder)
                taskId?.let { ToolTaskRegistry.progress(it, message = addresses.joinToString("\n").ifBlank { "Port $port • $folder" }) }
                manager.notify(NOTIFICATION, notification(addresses.firstOrNull() ?: "Port $port • $folder"))
            } catch (error: Throwable) {
                taskId?.let { ToolTaskRegistry.finish(it, if (error is CancellationException) ToolTaskStatus.CANCELLED else ToolTaskStatus.FAILED, error.message ?: "FTP-Start fehlgeschlagen") }
                candidate.close()
                if (error !is CancellationException) mutableState.value = FtpServerState(error = error.message ?: "FTP-Start fehlgeschlagen")
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }
    private fun notification(text: String): Notification {
        val stop = PendingIntent.getService(this, 31, Intent(this, FtpServerService::class.java).setAction(STOP), PendingIntent.FLAG_IMMUTABLE)
        val open = PendingIntent.getActivity(this, 32, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL).setSmallIcon(R.drawable.mt_ic_tools)
            .setContentTitle("MTApktool • FTP-Server").setContentText(text).setOngoing(true).setContentIntent(open)
            .addAction(0, "Stoppen", stop).build()
    }
    override fun onTimeout(startId: Int, fgsType: Int) { stopSelf() }
    override fun onDestroy() {
        taskId?.let { ToolTaskRegistry.finish(it, message = "FTP-Server beendet") }; taskId = null
        scope.cancel()
        server?.close(); server = null
        val error = mutableState.value.error
        mutableState.value = FtpServerState(error = error)
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }
    companion object {
        private const val STOP = "mt.ftp.STOP"
        private const val CHANNEL = "mt_ftp_server"
        private const val NOTIFICATION = 3121
        private val mutableState = MutableStateFlow(FtpServerState())
        val state = mutableState.asStateFlow()
        fun start(context: Context, folder: String, port: Int, user: String, password: String, writable: Boolean) {
            ContextCompat.startForegroundService(context, Intent(context, FtpServerService::class.java)
                .putExtra("folder", folder).putExtra("port", port).putExtra("user", user).putExtra("password", password).putExtra("writable", writable))
        }
        fun stop(context: Context) { context.stopService(Intent(context, FtpServerService::class.java)) }
        fun lanAddresses(): List<String> = runCatching {
            NetworkInterface.getNetworkInterfaces().toList().filter { it.isUp && !it.isLoopback }
                .flatMap { it.inetAddresses.toList() }.filter { !it.isLoopbackAddress && it is java.net.Inet4Address }
                .mapNotNull { it.hostAddress }.distinct()
        }.getOrDefault(emptyList())
    }
}
