package uk.nktnet.webviewkiosk.ui.components.setting

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import uk.nktnet.webviewkiosk.config.SystemSettings
import uk.nktnet.webviewkiosk.managers.UnifiedPushManager

@Composable
fun UnifiedPushControlButtons() {
    val context = LocalContext.current
    val systemSettings = remember { SystemSettings(context) }

    var savedDistributor by remember {
        mutableStateOf(UnifiedPushManager.getSavedDistributor(context))
    }
    var ackDistributor by remember {
        mutableStateOf(UnifiedPushManager.getAckDistributor(context))
    }
    var endpointUrl by remember {
        mutableStateOf(systemSettings.unifiedpushEndpointUrl)
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            savedDistributor = UnifiedPushManager.getSavedDistributor(context)
            ackDistributor = UnifiedPushManager.getAckDistributor(context)
            endpointUrl = systemSettings.unifiedpushEndpointUrl
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.small),
            shape = MaterialTheme.shapes.small,
            tonalElevation = 1.dp
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                InfoRow(
                    label = "Saved distributor",
                    value = savedDistributor
                )

                InfoRow(
                    label = "Ack distributor",
                    value = ackDistributor
                )

                InfoRow(
                    label = "Endpoint URL",
                    value = endpointUrl
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Button(
                onClick = { UnifiedPushManager.register(context) },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            ) {
                Text("Register")
            }

            Button(
                onClick = { UnifiedPushManager.unregister(context) },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                )
            ) {
                Text("Unregister")
            }
        }
    }
}

@Composable
private fun InfoRow(
    label: String,
    value: String?
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        SelectionContainer {
            Text(
                text = value?.takeIf { it.isNotBlank() } ?: "None",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Normal
                ),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
