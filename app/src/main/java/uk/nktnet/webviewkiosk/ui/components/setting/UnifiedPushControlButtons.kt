package uk.nktnet.webviewkiosk.ui.components.setting

import android.content.ClipData
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.toClipEntry
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import uk.nktnet.webviewkiosk.R
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
                val status = if (savedDistributor.isNullOrBlank()) {
                    ""
                } else if (savedDistributor != ackDistributor) {
                    " (pending)"
                } else {
                    " (ready)"
                }
                InfoRow(
                    label = "Distributor${status}",
                    value = savedDistributor
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
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val displayValue = value?.takeIf { it.isNotBlank() } ?: "None"

    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f).padding(end = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                SelectionContainer {
                    Text(
                        text = displayValue,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Normal
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            IconButton(
                enabled = !value.isNullOrBlank(),
                modifier = Modifier.size(24.dp),
                onClick = {
                    scope.launch {
                        val clipData = ClipData.newPlainText(label, value)
                        clipboard.setClipEntry(clipData.toClipEntry())
                    }
                }
            ) {
                Icon(
                    painter = painterResource(R.drawable.baseline_content_copy_24),
                    contentDescription = null
                )
            }
        }
    }
}
