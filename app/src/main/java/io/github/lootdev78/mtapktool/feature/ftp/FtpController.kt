package io.github.lootdev78.mtapktool.feature.ftp

import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.text.format.Formatter
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.net.Socket

class FtpController(private val context: Context) {

    companion object {
        const val TAG = "FtpController"
        var ftpServerRunning = false
    }

    private val profileManager = FtpProfileManager(context)
    private val scope = CoroutineScope(Dispatchers.Main)

    fun getServerIP(): String {
        val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val ipAddress = wifiManager?.connectionInfo?.ipAddress ?: 0
        return Formatter.formatIpAddress(ipAddress)
    }

    fun startFtpServer(
        port: Int,
        username: String,
        password: String,
        securityType: Int = 0,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        scope.launch(Dispatchers.IO) {
            try {
                // FTP Server initialization would go here
                // This requires the FTP4J/FTPServer libraries
                ftpServerRunning = true
                onSuccess()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start FTP server", e)
                onError(e.message ?: "Unknown error")
            }
        }
    }

    fun stopFtpServer(onSuccess: () -> Unit = {}) {
        scope.launch(Dispatchers.IO) {
            try {
                ftpServerRunning = false
                onSuccess()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to stop FTP server", e)
            }
        }
    }

    fun getProfiles(isServer: Boolean): List<FtpProfile> {
        return if (isServer) profileManager.getServerProfiles() else profileManager.getClientProfiles()
    }

    fun addProfile(profile: FtpProfile) {
        profileManager.addProfile(profile)
    }

    fun updateProfile(index: Int, profile: FtpProfile) {
        profileManager.updateProfile(index, profile)
    }

    fun deleteProfile(index: Int) {
        profileManager.deleteProfile(index)
    }
}