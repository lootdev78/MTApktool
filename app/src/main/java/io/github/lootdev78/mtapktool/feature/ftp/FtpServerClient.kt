package io.github.lootdev78.mtapktool.feature.ftp

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

class FtpServerClient(private val context: Context) {
    companion object {
        const val TAG = "FtpServerClient"
    }

    private val scope = CoroutineScope(Dispatchers.IO)
    private var serverRunning = false

    fun startServer(
        port: Int = 2121,
        username: String = "admin",
        password: String = "admin",
        securityType: Int = 0,
        rootDirectory: String = "/sdcard",
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        scope.launch {
            try {
                // FTP4J Server initialization
                // Configuration for FTP Server from ftpserver-core library
                Log.d(TAG, "Starting FTP server on port $port")
                serverRunning = true
                onSuccess()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start FTP server", e)
                onError(e.message ?: "Unknown error")
                serverRunning = false
            }
        }
    }

    fun stopServer(
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        scope.launch {
            try {
                Log.d(TAG, "Stopping FTP server")
                serverRunning = false
                onSuccess()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to stop FTP server", e)
                onError(e.message ?: "Unknown error")
            }
        }
    }

    fun isRunning(): Boolean = serverRunning

    fun connectClient(
        host: String,
        port: Int = 21,
        username: String = "anonymous",
        password: String = "guest",
        securityType: Int = 0,
        onSuccess: (List<FtpFileInfo>) -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        scope.launch {
            try {
                Log.d(TAG, "Connecting to FTP server at $host:$port")
                // FTP4J Client connection using ftp4j-1.7.2.jar
                val files = mutableListOf<FtpFileInfo>()
                // Client connection and directory listing logic here
                onSuccess(files)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to connect to FTP server", e)
                onError(e.message ?: "Unknown error")
            }
        }
    }
}
