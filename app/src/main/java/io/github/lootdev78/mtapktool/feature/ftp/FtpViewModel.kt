package io.github.lootdev78.mtapktool.feature.ftp

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import android.net.wifi.WifiManager
import android.text.format.Formatter

data class FtpUiState(
    val serverRunning: Boolean = false,
    val serverIP: String = "",
    val serverPort: Int = 2121,
    val isClientConnected: Boolean = false,
    val clientCurrentPath: String = "/",
    val fileList: List<FtpFileInfo> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val selectedProfile: FtpProfile? = null
)

class FtpViewModel(application: Application) : AndroidViewModel(application) {
    private val context = application.applicationContext
    private val profileManager = FtpProfileManager(context)
    private val serverManager = FtpServerManager(context)
    private val clientManager = FtpClientManager(context)
    
    private val _uiState = MutableStateFlow(FtpUiState())
    val uiState: StateFlow<FtpUiState> = _uiState.asStateFlow()

    fun startServer(profile: FtpProfile) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            serverManager.startServer(
                port = profile.port,
                username = profile.username,
                password = profile.password,
                rootDir = "/sdcard",
                scope = viewModelScope,
                onStarted = {
                    _uiState.value = _uiState.value.copy(
                        serverRunning = true,
                        serverIP = getLocalIP(),
                        serverPort = profile.port,
                        isLoading = false,
                        selectedProfile = profile
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
        serverManager.stopServer()
        _uiState.value = _uiState.value.copy(
            serverRunning = false,
            isLoading = false
        )
    }

    fun connectToServer(profile: FtpProfile) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            clientManager.connect(
                host = profile.ip,
                port = profile.port,
                username = profile.username,
                password = profile.password,
                scope = viewModelScope,
                onConnected = {
                    _uiState.value = _uiState.value.copy(
                        isClientConnected = true,
                        isLoading = false,
                        selectedProfile = profile
                    )
                    listRemoteFiles("/")
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

    fun disconnectClient() {
        clientManager.disconnect()
        _uiState.value = _uiState.value.copy(
            isClientConnected = false,
            fileList = emptyList()
        )
    }

    fun listRemoteFiles(path: String = "/") {
        if (!clientManager.isConnected()) {
            _uiState.value = _uiState.value.copy(
                errorMessage = "Not connected to server"
            )
            return
        }

        clientManager.listFiles(
            path = path,
            scope = viewModelScope,
            onFilesReceived = { files ->
                _uiState.value = _uiState.value.copy(
                    fileList = files,
                    clientCurrentPath = path,
                    isLoading = false
                )
            },
            onError = { error ->
                _uiState.value = _uiState.value.copy(
                    errorMessage = error,
                    isLoading = false
                )
            }
        )
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

    private fun getLocalIP(): String {
        val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val ipAddress = wifiManager?.connectionInfo?.ipAddress ?: 0
        return Formatter.formatIpAddress(ipAddress)
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    override fun onCleared() {
        super.onCleared()
        if (serverManager.isServerRunning()) {
            stopServer()
        }
        if (clientManager.isConnected()) {
            disconnectClient()
        }
    }
}
