package io.github.lootdev78.mtapktool.feature.ftp

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class FtpUiState(
    val serverRunning: Boolean = false,
    val serverIP: String = "",
    val serverPort: Int = 2121,
    val isConnected: Boolean = false,
    val currentPath: String = "/",
    val fileList: List<FtpFileInfo> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

class FtpViewModel(application: Application) : AndroidViewModel(application) {
    private val context = application.applicationContext
    private val profileManager = FtpProfileManager(context)
    private val ftpClient = FtpServerClient(context)
    
    private val _uiState = MutableStateFlow(FtpUiState())
    val uiState: StateFlow<FtpUiState> = _uiState.asStateFlow()

    fun startServer(profile: FtpProfile) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            ftpClient.startServer(
                port = profile.port,
                username = profile.username,
                password = profile.password,
                securityType = profile.securityType,
                onSuccess = {
                    _uiState.value = _uiState.value.copy(
                        serverRunning = true,
                        serverIP = getLocalIP(),
                        serverPort = profile.port,
                        isLoading = false
                    )
                },
                onError = { error ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = error
                    )
                }
            )
        }
    }

    fun stopServer() {
        viewModelScope.launch {
            ftpClient.stopServer(
                onSuccess = {
                    _uiState.value = _uiState.value.copy(
                        serverRunning = false,
                        isLoading = false
                    )
                },
                onError = { error ->
                    _uiState.value = _uiState.value.copy(
                        errorMessage = error
                    )
                }
            )
        }
    }

    fun connectToServer(profile: FtpProfile) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            ftpClient.connectClient(
                host = profile.ip,
                port = profile.port,
                username = profile.username,
                password = profile.password,
                securityType = profile.securityType,
                onSuccess = { files ->
                    _uiState.value = _uiState.value.copy(
                        isConnected = true,
                        fileList = files,
                        isLoading = false
                    )
                },
                onError = { error ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = error
                    )
                }
            )
        }
    }

    fun getProfiles(isServer: Boolean): List<FtpProfile> {
        return if (isServer) profileManager.getServerProfiles() else profileManager.getClientProfiles()
    }

    fun addProfile(profile: FtpProfile) {
        profileManager.addProfile(profile)
    }

    fun deleteProfile(index: Int) {
        profileManager.deleteProfile(index)
    }

    private fun getLocalIP(): String {
        val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as? android.net.wifi.WifiManager
        val ipAddress = wifiManager?.connectionInfo?.ipAddress ?: 0
        return android.text.format.Formatter.formatIpAddress(ipAddress)
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }
}
