package io.github.lootdev78.mtapktool.feature.ftp

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.Socket

class FtpClientManager(private val context: Context) {
    companion object {
        const val TAG = "FtpClientManager"
    }

    private var clientSocket: Socket? = null
    private var reader: BufferedReader? = null
    private var writer: PrintWriter? = null
    private var isConnected = false

    fun connect(
        host: String,
        port: Int = 21,
        username: String = "anonymous",
        password: String = "guest",
        scope: CoroutineScope,
        onConnected: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        scope.launch(Dispatchers.IO) {
            try {
                clientSocket = Socket(host, port)
                reader = BufferedReader(InputStreamReader(clientSocket!!.getInputStream()))
                writer = PrintWriter(clientSocket!!.getOutputStream(), true)

                val welcomeResponse = reader!!.readLine()
                Log.d(TAG, "Server: $welcomeResponse")

                writer!!.println("USER $username")
                val userResponse = reader!!.readLine()
                Log.d(TAG, "Response: $userResponse")

                writer!!.println("PASS $password")
                val passResponse = reader!!.readLine()
                Log.d(TAG, "Response: $passResponse")

                isConnected = passResponse?.startsWith("230") ?: false

                if (isConnected) {
                    withContext(Dispatchers.Main) {
                        onConnected()
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        onError("Authentication failed: $passResponse")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Connection failed", e)
                withContext(Dispatchers.Main) {
                    onError(e.message ?: "Connection error")
                }
                isConnected = false
            }
        }
    }

    fun listFiles(
        path: String = "/",
        scope: CoroutineScope,
        onFilesReceived: (List<FtpFileInfo>) -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        if (!isConnected) {
            onError("Not connected")
            return
        }

        scope.launch(Dispatchers.IO) {
            try {
                writer!!.println("CWD $path")
                val cwdResponse = reader!!.readLine()
                Log.d(TAG, "CWD Response: $cwdResponse")

                writer!!.println("LIST")
                val listResponse = reader!!.readLine()
                Log.d(TAG, "LIST Response: $listResponse")

                val files = mutableListOf<FtpFileInfo>()
                if (listResponse?.startsWith("150") == true) {
                    var line = reader!!.readLine()
                    while (line != null && !line.startsWith("226")) {
                        try {
                            val parts = line.split("\\s+".toRegex())
                            if (parts.size >= 9) {
                                val isDir = line.startsWith("d")
                                val size = parts[4].toLongOrNull() ?: 0L
                                val name = parts.subList(8, parts.size).joinToString(" ")
                                files.add(
                                    FtpFileInfo(
                                        name = name,
                                        path = "$path/$name",
                                        isDirectory = isDir,
                                        size = size,
                                        modifiedTime = System.currentTimeMillis()
                                    )
                                )
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error parsing file listing", e)
                        }
                        line = reader!!.readLine()
                    }
                }

                withContext(Dispatchers.Main) {
                    onFilesReceived(files)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error listing files", e)
                withContext(Dispatchers.Main) {
                    onError(e.message ?: "Error listing files")
                }
            }
        }
    }

    fun disconnect() {
        try {
            writer?.println("QUIT")
            reader?.readLine()
            clientSocket?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error disconnecting", e)
        }
        isConnected = false
    }

    fun isConnected(): Boolean = isConnected
}
