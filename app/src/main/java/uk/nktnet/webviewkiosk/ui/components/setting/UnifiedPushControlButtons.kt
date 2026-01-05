package uk.nktnet.webviewkiosk.ui.components.setting

import android.content.ClipData
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import uk.nktnet.webviewkiosk.config.unifiedpush.UnifiedPushEndpoint
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
    var endpoint by remember {
        mutableStateOf(systemSettings.unifiedpushEndpoint)
    }
    var expanded by remember { mutableStateOf(false) }
    var showValuesCheckbox by remember { mutableStateOf(false) }

    val isRedacted = endpoint?.redacted == true
    val showValues = isRedacted || showValuesCheckbox

    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            savedDistributor = UnifiedPushManager.getSavedDistributor(context)
            ackDistributor = UnifiedPushManager.getAckDistributor(context)
            if (ackDistributor.isNullOrBlank()) {
                endpoint = null
                systemSettings.unifiedpushEndpoint = null
            } else {
                endpoint = systemSettings.unifiedpushEndpoint
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outline,
                    shape = MaterialTheme.shapes.small
                ),
            shape = MaterialTheme.shapes.small,
            tonalElevation = 1.dp
        ) {
            Column(
                modifier = Modifier
                    .animateContentSize()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val status = when {
                    savedDistributor.isNullOrBlank() -> ""
                    savedDistributor != ackDistributor -> " (pending)"
                    else -> if (endpoint?.temporary ?: false) {
                        " (ready, temporary)"
                    } else {
                        " (ready)"
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Distributor$status",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(2.dp))
                        SelectionContainer {
                            Text(
                                text = savedDistributor ?: "None",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = FontFamily.Monospace
                                ),
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    IconButton(
                        onClick = { expanded = !expanded },
                    ) {
                        Icon(
                            painter = painterResource(
                                if (expanded)
                                    R.drawable.keyboard_arrow_up_24
                                else
                                    R.drawable.keyboard_arrow_down_24
                            ),
                            contentDescription = null
                        )
                    }
                }

                if (expanded) {
                    HorizontalDivider(
                        Modifier.padding(top = 6.dp, bottom = 12.dp),
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                        InfoRow(
                            label = "Endpoint URL",
                            value = endpoint?.url,
                            showValues = showValues,
                        )
                        InfoRow(
                            label = "Endpoint Public Key",
                            value = endpoint?.pubKeySet?.pubKey,
                            showValues = showValues,
                        )
                        InfoRow(
                            label = "Endpoint Auth Secret",
                            value = endpoint?.pubKeySet?.auth,
                            showValues = showValues,
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(
                                enabled = endpoint != null && !isRedacted,
                                colors = ButtonDefaults.textButtonColors(
                                    contentColor = MaterialTheme.colorScheme.error,
                                ),
                                onClick = {
                                    val redactedEndpoint = UnifiedPushEndpoint.createRedactEndpoint(
                                        endpoint?.temporary ?: false
                                    )
                                    systemSettings.unifiedpushEndpoint = redactedEndpoint
                                    endpoint = redactedEndpoint
                                }
                            ) {
                                Text(
                                    text = "Redact",
                                )
                            }

                            Spacer(modifier = Modifier.weight(1f))

                            Checkbox(
                                enabled = !isRedacted,
                                checked = isRedacted || showValuesCheckbox,
                                onCheckedChange = { showValuesCheckbox = it }
                            )
                            Text(
                                text = "Show Values",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier
                                    .clickable(
                                        indication = null,
                                        interactionSource = remember { MutableInteractionSource() }
                                    ) {
                                        if (!isRedacted) {
                                            showValuesCheckbox = !showValuesCheckbox
                                        }
                                    }
                            )
                        }
                    }
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
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
        }
    }
}

@Composable
private fun InfoRow(
    label: String,
    value: String?,
    showValues: Boolean,
) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val displayValue = if (value.isNullOrEmpty()) {
        "None"
    } else if (!showValues) {
        "*".repeat(value.length)
    } else {
        value
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f).padding(end = 8.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            SelectionContainer {
                Text(
                    text = displayValue,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = if (showValues) {
                        TextOverflow.Ellipsis
                    } else {
                        TextOverflow.Clip
                    }
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
