package io.github.lootdev78.mtapktool.feature.ftp

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import java.io.File
import java.net.Socket
import java.net.ServerSocket
import java.io.PrintWriter
import java.io.BufferedReader
import java.io.InputStreamReader

class FtpServerManager(private val context: Context) {
    companion object {
        const val TAG = "FtpServerManager"
        private var serverSocket: ServerSocket? = null
        private var isRunning = false
    }

    private var serverJob: Job? = null

    fun startServer(
        port: Int = 2121,
        username: String = "admin",
        password: String = "admin",
        rootDir: String = "/sdcard",
        scope: CoroutineScope,
        onStarted: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        if (isRunning) {
            onError("Server already running")
            return
        }

        serverJob = scope.launch(Dispatchers.IO) {
            try {
                serverSocket = ServerSocket(port)
                isRunning = true
                Log.d(TAG, "FTP Server started on port $port")
                withContext(Dispatchers.Main) {
                    onStarted()
                }

                while (isRunning && !Thread.currentThread().isInterrupted) {
                    try {
                        val clientSocket = serverSocket?.accept() ?: break
                        scope.launch(Dispatchers.IO) {
                            handleClientConnection(clientSocket, username, password, rootDir)
                        }
                    } catch (e: Exception) {
                        if (isRunning) {
                            Log.e(TAG, "Error accepting client", e)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start FTP server", e)
                withContext(Dispatchers.Main) {
                    onError(e.message ?: "Unknown error")
                }
            }
        }
    }

    private suspend fun handleClientConnection(
        socket: Socket,
        username: String,
        password: String,
        rootDir: String
    ) {
        return withContext(Dispatchers.IO) {
            try {
                val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
                val writer = PrintWriter(socket.getOutputStream(), true)

                writer.println("220 FTP Server Ready")

                var authenticated = false
                var currentUser = ""

                while (socket.isConnected) {
                    val line = reader.readLine() ?: break
                    val parts = line.split(" ", limit = 2)
                    val command = parts.getOrNull(0)?.uppercase() ?: continue
                    val argument = parts.getOrNull(1) ?: ""

                    when (command) {
                        "USER" -> {
                            currentUser = argument
                            writer.println("331 Password required")
                        }
                        "PASS" -> {
                            if (currentUser == username && argument == password) {
                                authenticated = true
                                writer.println("230 Login successful")
                            } else {
                                writer.println("530 Login failed")
                                authenticated = false
                            }
                        }
                        "QUIT" -> {
                            writer.println("221 Goodbye")
                            break
                        }
                        "PWD" -> {
                            if (authenticated) {
                                writer.println("257 \"/\" is the current directory")
                            } else {
                                writer.println("530 Not logged in")
                            }
                        }
                        "LIST" -> {
                            if (authenticated) {
                                writer.println("150 File listing")
                                val dir = File(rootDir)
                                dir.listFiles()?.forEach { file ->
                                    val permissions = if (file.isDirectory) "d" else "-"
                                    val size = file.length()
                                    val name = file.name
                                    writer.println("$permissions---------- 1 user group $size Jan 01 00:00 $name")
                                }
                                writer.println("226 Listing complete")
                            } else {
                                writer.println("530 Not logged in")
                            }
                        }
                        "TYPE" -> {
                            writer.println("200 Type set to ${argument.uppercase()}")
                        }
                        "NOOP" -> {
                            writer.println("200 NOOP ok")
                        }
                        else -> {
                            writer.println("502 Command not implemented")
                        }
                    }
                }

                socket.close()
            } catch (e: Exception) {
                Log.e(TAG, "Error handling client", e)
            }
        }
    }

    fun stopServer() {
        isRunning = false
        serverJob?.cancel()
        try {
            serverSocket?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing server socket", e)
        }
        serverSocket = null
        Log.d(TAG, "FTP Server stopped")
    }

    fun isServerRunning(): Boolean = isRunning
}
