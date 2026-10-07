package io.github.lootdev78.mtapktool.feature.ftp

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.lootdev78.mtapktool.core.theme.MtClassicAlertDialog

@Composable
fun FtpClientDialog(
    onDismiss: () -> Unit,
    onConnect: (ip: String, port: Int, username: String, password: String, security: Int) -> Unit
) {
    var ip by remember { mutableStateOf("192.168.1.1") }
    var port by remember { mutableStateOf("2121") }
    var username by remember { mutableStateOf("admin") }
    var password by remember { mutableStateOf("admin") }
    var selectedSecurity by remember { mutableStateOf(0) }
    val securityOptions = listOf("FTP", "FTPS (Explicit)", "FTPS (Implicit)")

    MtClassicAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Connect to FTP Server") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = ip,
                    onValueChange = { ip = it },
                    label = { Text("Server IP") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                )
                OutlinedTextField(
                    value = port,
                    onValueChange = { port = it },
                    label = { Text("Port") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                )
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Username") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                )
                Text("Security Type", style = MaterialTheme.typography.labelMedium)
                securityOptions.forEachIndexed { index, option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp)
                    ) {
                        RadioButton(
                            selected = selectedSecurity == index,
                            onClick = { selectedSecurity = index }
                        )
                        Text(option, modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConnect(
                        ip,
                        port.toIntOrNull() ?: 2121,
                        username,
                        password,
                        selectedSecurity
                    )
                    onDismiss()
                }
            ) { Text("Connect") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}