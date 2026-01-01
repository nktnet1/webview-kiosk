package uk.nktnet.webviewkiosk.ui.components.setting

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import uk.nktnet.webviewkiosk.managers.ToastManager
import uk.nktnet.webviewkiosk.managers.UnifiedPushManager

@Composable
fun UnifiedPushControlButtons() {
    val context = LocalContext.current
    var isProcessing by remember { mutableStateOf(false) }
    var savedDistributor by remember { mutableStateOf(UnifiedPushManager.getSavedDistributor(context)) }
    var ackDistributor by remember { mutableStateOf(UnifiedPushManager.getAckDistributor(context)) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(2000)
            savedDistributor = UnifiedPushManager.getSavedDistributor(context)
            ackDistributor = UnifiedPushManager.getAckDistributor(context)
            println("[DEBUG] $savedDistributor | $ackDistributor")
        }
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
                .padding(bottom = 4.dp)
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.primary,
                    shape = MaterialTheme.shapes.small
                )
                .padding(vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row {
                    Text(
                        text = "Saved Distributor: ",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = savedDistributor ?: "None",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Row {
                    Text(
                        text = "Ack Distributor: ",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = ackDistributor ?: "None",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Button(
                enabled = !isProcessing,
                onClick = {
                    UnifiedPushManager.register(context)
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            ) {
                Text("Register")
            }

            Button(
                enabled = !isProcessing,
                onClick = { /* unregister logic */ },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                )
            ) {
                Text("Unregister")
            }
        }

        Button(
            enabled = !isProcessing,
            onClick = {
                ToastManager.show(
                    context,
                    "Saved Distributor: ${savedDistributor ?: "None"}, Ack Distributor: ${ackDistributor ?: "None"}"
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp)
        ) {
            Text("Check Distributors")
        }
    }
}
