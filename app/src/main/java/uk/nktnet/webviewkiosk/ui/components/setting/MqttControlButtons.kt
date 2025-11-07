package uk.nktnet.webviewkiosk.ui.components.setting

import android.widget.Toast
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import kotlinx.coroutines.delay
import uk.nktnet.webviewkiosk.config.Screen
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.mqtt.MqttManager
import com.hivemq.client.mqtt.MqttClientState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun MqttControlButtons(navController: NavController) {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }
    var mqttClientState by remember { mutableStateOf(MqttManager.getState()) }

    var toastRef: Toast? = null
    val coroutineScope = rememberCoroutineScope()
    val showToast: (String) -> Unit = { msg ->
        coroutineScope.launch(Dispatchers.Main) {
            toastRef?.cancel()
            toastRef = Toast.makeText(context, msg, Toast.LENGTH_SHORT)
                .apply { show() }
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            mqttClientState = MqttManager.getState()
            delay(500)
        }
    }

    mqttClientState.let { state ->
        val statusColor = when (state) {
            MqttClientState.CONNECTING,
            MqttClientState.CONNECTING_RECONNECT -> MaterialTheme.colorScheme.tertiary
            MqttClientState.CONNECTED -> MaterialTheme.colorScheme.primary
            MqttClientState.DISCONNECTED_RECONNECT -> MaterialTheme.colorScheme.secondary
            MqttClientState.DISCONNECTED -> MaterialTheme.colorScheme.error
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .border(
                        width = 1.dp,
                        color = statusColor,
                        shape = MaterialTheme.shapes.small
                    )
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Row {
                    Text(
                        text = "Status: ",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = state.toString(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = statusColor
                    )
                }
            }

            if (state.isConnected) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Button(
                        onClick = {
                            MqttManager.disconnect {
                                mqttClientState = MqttManager.getState()
                                showToast("MQTT disconnected successfully")
                            }
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) { Text("Disconnect") }

                    Button(
                        onClick = {
                            mqttClientState = MqttClientState.CONNECTING
                            MqttManager.disconnect(
                                onDisconnected = {
                                    mqttClientState = MqttManager.getState()
                                    MqttManager.connect(
                                        userSettings,
                                        onConnected = {
                                            mqttClientState = MqttManager.getState()
                                            showToast("Reconnected successfully.")
                                        },
                                        onError = {
                                            mqttClientState = MqttManager.getState()
                                            showToast("Error reconnecting: $it")
                                        }
                                    )
                                },
                                onError = {
                                    mqttClientState = MqttManager.getState()
                                    showToast("Error disconnecting: $it")
                                }
                            )

                        },
                        modifier = Modifier.weight(1f)
                    ) { Text("Reconnect") }
                }
            } else if (
                state in listOf(
                    MqttClientState.CONNECTING,
                    MqttClientState.CONNECTING_RECONNECT,
                    MqttClientState.DISCONNECTED_RECONNECT
                )
            ) {
                Button(
                    onClick = {
                        val res = MqttManager.cancelConnect()
                        if (res) {
                            showToast("Requested cancellation...")
                        } else {
                            showToast("Cancellation is already requested.")
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.secondary
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Cancel Connection") }
            } else {
                Button(
                    onClick = {
                        mqttClientState = MqttClientState.CONNECTING
                        MqttManager.connect(
                            userSettings,
                            onConnected = {
                                mqttClientState = MqttManager.getState()
                                showToast("MQTT connected successfully.")
                            },
                            onError = {
                                mqttClientState = MqttManager.getState()
                                showToast("MQTT connection failed: $it")
                            }
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Connect") }
            }

            Button(
                onClick = {
                    navController.navigate(Screen.SettingsMqttDebug.route)
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Debug Logs") }
        }
    }
}
