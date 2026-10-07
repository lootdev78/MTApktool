package io.github.lootdev78.mtapktool.feature.ftp

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun FtpIntegrationScreen(
    onClose: () -> Unit,
    ftpViewModel: FtpViewModel = viewModel()
) {
    val uiState by ftpViewModel.uiState.collectAsState()
    var showServerDialog by remember { mutableStateOf(false) }
    var showClientDialog by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableStateOf(0) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TabRow(selectedTabIndex = selectedTab, modifier = Modifier.fillMaxWidth()) {
                Tab(
                    text = { Text("Server") },
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 }
                )
                Tab(
                    text = { Text("Client") },
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 }
                )
            }
        }

        when (selectedTab) {
            0 -> FtpServerPanel(
                uiState = uiState,
                onStart = { showServerDialog = true },
                onStop = { ftpViewModel.stopServer() },
                getProfiles = { ftpViewModel.getProfiles(true) },
                onProfileSelect = { profile -> ftpViewModel.startServer(profile) },
                onAddProfile = { showServerDialog = true }
            )
            1 -> FtpClientPanel(
                uiState = uiState,
                onConnect = { showClientDialog = true },
                getProfiles = { ftpViewModel.getProfiles(false) },
                onProfileSelect = { profile -> ftpViewModel.connectToServer(profile) },
                onAddProfile = { showClientDialog = true }
            )
        }

        if (showServerDialog) {
            FtpServerDialog(
                onDismiss = { showServerDialog = false },
                onStart = { port, username, password, security ->
                    val profile = FtpProfile(
                        name = "Server $port",
                        ip = "0.0.0.0",
                        port = port,
                        username = username,
                        password = password,
                        isServerProfile = true,
                        securityType = security
                    )
                    ftpViewModel.addProfile(profile)
                    ftpViewModel.startServer(profile)
                    showServerDialog = false
                }
            )
        }

        if (showClientDialog) {
            FtpClientDialog(
                onDismiss = { showClientDialog = false },
                onConnect = { ip, port, username, password, security ->
                    val profile = FtpProfile(
                        name = "$ip:$port",
                        ip = ip,
                        port = port,
                        username = username,
                        password = password,
                        isServerProfile = false,
                        securityType = security
                    )
                    ftpViewModel.addProfile(profile)
                    ftpViewModel.connectToServer(profile)
                    showClientDialog = false
                }
            )
        }
    }
}

@Composable
fun FtpServerPanel(
    uiState: FtpUiState,
    onStart: () -> Unit,
    onStop: () -> Unit,
    getProfiles: () -> List<FtpProfile>,
    onProfileSelect: (FtpProfile) -> Unit,
    onAddProfile: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        if (uiState.serverRunning) {
            Card(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Server Running", style = MaterialTheme.typography.titleMedium)
                    Text("IP: ${uiState.serverIP}:${uiState.serverPort}")
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = onStop,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Default.Stop, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Stop Server")
                    }
                }
            }
        } else {
            Text("Saved Server Profiles", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(bottom = 8.dp))
            getProfiles().forEach { profile ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(profile.name, style = MaterialTheme.typography.bodyMedium)
                            Text("Port: ${profile.port}", style = MaterialTheme.typography.bodySmall)
                        }
                        Button(
                            onClick = { onProfileSelect(profile) },
                            modifier = Modifier.padding(start = 8.dp)
                        ) {
                            Text("Start")
                        }
                    }
                }
            }
            Button(
                onClick = onAddProfile,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("New Profile")
            }
        }
    }
}

@Composable
fun FtpClientPanel(
    uiState: FtpUiState,
    onConnect: () -> Unit,
    getProfiles: () -> List<FtpProfile>,
    onProfileSelect: (FtpProfile) -> Unit,
    onAddProfile: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        if (uiState.isConnected) {
            Card(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Connected", style = MaterialTheme.typography.titleMedium)
                    Text("Path: ${uiState.currentPath}", style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Files: ${uiState.fileList.size}")
                }
            }
        } else {
            Text("Saved Connection Profiles", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(bottom = 8.dp))
            getProfiles().forEach { profile ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(profile.name, style = MaterialTheme.typography.bodyMedium)
                            Text("${profile.ip}:${profile.port}", style = MaterialTheme.typography.bodySmall)
                        }
                        Button(
                            onClick = { onProfileSelect(profile) },
                            modifier = Modifier.padding(start = 8.dp)
                        ) {
                            Text("Connect")
                        }
                    }
                }
            }
            Button(
                onClick = onAddProfile,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("New Connection")
            }
        }
    }
}
