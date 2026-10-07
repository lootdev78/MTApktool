package io.github.lootdev78.mtapktool.feature.ftp

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun FtpIntegrationScreen(
    modifier: Modifier = Modifier,
    ftpViewModel: FtpViewModel = viewModel()
) {
    val uiState by ftpViewModel.uiState.collectAsState()
    var showServerDialog by remember { mutableStateOf(false) }
    var showClientDialog by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableStateOf(0) }

    Column(modifier = modifier.fillMaxSize()) {
        // Tab Navigation
        TabRow(
            selectedTabIndex = selectedTab,
            modifier = Modifier.fillMaxWidth()
        ) {
            Tab(
                text = { Text("FTP Server") },
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                icon = { Icon(Icons.Default.Storage, contentDescription = null) }
            )
            Tab(
                text = { Text("FTP Client") },
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                icon = { Icon(Icons.Default.Cloud, contentDescription = null) }
            )
        }

        // Error Message
        if (uiState.errorMessage != null) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        uiState.errorMessage ?: "",
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { ftpViewModel.clearError() }) {
                        Icon(Icons.Default.Close, contentDescription = "Dismiss")
                    }
                }
            }
        }

        // Tab Content
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            when (selectedTab) {
                0 -> FtpServerPanel(
                    uiState = uiState,
                    onStartServer = { showServerDialog = true },
                    onStopServer = { ftpViewModel.stopServer() },
                    serverProfiles = ftpViewModel.getProfiles(true),
                    onSelectProfile = { ftpViewModel.startServer(it) },
                    onDeleteProfile = { index -> ftpViewModel.deleteProfile(index) }
                )
                1 -> FtpClientPanel(
                    uiState = uiState,
                    onConnectClient = { showClientDialog = true },
                    onDisconnectClient = { ftpViewModel.disconnectClient() },
                    clientProfiles = ftpViewModel.getProfiles(false),
                    onSelectProfile = { ftpViewModel.connectToServer(it) },
                    onListFiles = { path -> ftpViewModel.listRemoteFiles(path) },
                    onDeleteProfile = { index -> ftpViewModel.deleteProfile(index) }
                )
            }
        }
    }

    // Dialogs
    if (showServerDialog) {
        FtpServerDialog(
            onDismiss = { showServerDialog = false },
            onStart = { port, username, password, security ->
                val profile = FtpProfile(
                    name = "Server-$port",
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
                    name = "Client-$ip",
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

@Composable
fun FtpServerPanel(
    uiState: FtpUiState,
    onStartServer: () -> Unit,
    onStopServer: () -> Unit,
    serverProfiles: List<FtpProfile>,
    onSelectProfile: (FtpProfile) -> Unit,
    onDeleteProfile: (Int) -> Unit
) {
    if (uiState.serverRunning) {
        Column(modifier = Modifier.fillMaxSize()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                "FTP Server Active",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "IP: ${uiState.serverIP}",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                "Port: ${uiState.serverPort}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Button(
                onClick = onStopServer,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Stop Server")
            }
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                "Available Server Profiles",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            if (serverProfiles.isEmpty()) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                ) {
                    Text(
                        "No server profiles. Create one below.",
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            } else {
                serverProfiles.forEachIndexed { index, profile ->
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
                                Text(
                                    "Port: ${profile.port} | User: ${profile.username}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Row {
                                Button(
                                    onClick = { onSelectProfile(profile) },
                                    modifier = Modifier.size(40.dp),
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                                }
                                IconButton(
                                    onClick = { onDeleteProfile(index) }
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete")
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onStartServer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("New Server Profile")
            }
        }
    }
}

@Composable
fun FtpClientPanel(
    uiState: FtpUiState,
    onConnectClient: () -> Unit,
    onDisconnectClient: () -> Unit,
    clientProfiles: List<FtpProfile>,
    onSelectProfile: (FtpProfile) -> Unit,
    onListFiles: (String) -> Unit,
    onDeleteProfile: (Int) -> Unit
) {
    if (uiState.isClientConnected) {
        Column(modifier = Modifier.fillMaxSize()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                "Connected",
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                uiState.selectedProfile?.ip ?: "Unknown",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Text(
                "Path: ${uiState.clientCurrentPath}",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            if (uiState.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .size(40.dp)
                        .padding(bottom = 12.dp)
                )
            }

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
            ) {
                items(uiState.fileList) { file ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(modifier = Modifier.weight(1f)) {
                                Icon(
                                    if (file.isDirectory) Icons.Default.Folder else Icons.Default.Description,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        file.name,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    if (!file.isDirectory) {
                                        Text(
                                            "${file.size} bytes",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Button(
                onClick = onDisconnectClient,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("Disconnect")
            }
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                "Available Connections",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            if (clientProfiles.isEmpty()) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                ) {
                    Text(
                        "No saved connections. Create one below.",
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            } else {
                clientProfiles.forEachIndexed { index, profile ->
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
                                Text(
                                    "${profile.ip}:${profile.port}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Row {
                                Button(
                                    onClick = { onSelectProfile(profile) },
                                    modifier = Modifier.size(40.dp),
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Icon(Icons.Default.Login, contentDescription = null)
                                }
                                IconButton(
                                    onClick = { onDeleteProfile(index) }
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete")
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onConnectClient,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("New Connection")
            }
        }
    }
}
