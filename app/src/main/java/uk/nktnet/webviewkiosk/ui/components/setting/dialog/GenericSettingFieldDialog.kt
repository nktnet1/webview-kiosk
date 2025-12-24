package com.nktnet.webview_kiosk.ui.components.setting.dialog

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.nktnet.webview_kiosk.R
import com.nktnet.webview_kiosk.utils.normaliseInfoText

@Composable
fun GenericSettingFieldDialog(
    title: String,
    infoText: String,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    settingKey: String,
    restricted: Boolean,
    content: @Composable ColumnScope.() -> Unit
) {
    var showInfoDialog by remember { mutableStateOf(false) }

    if (showInfoDialog) {
        AlertDialog(
            onDismissRequest = { showInfoDialog = false },
            title = {
                SelectionContainer {
                    Column {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            settingKey,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider()
                    }
                }
            },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                ) {
                    SelectionContainer {
                        Text(
                            normaliseInfoText(infoText)
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { showInfoDialog = false }
                ) {
                    Text("Close")
                }
            },
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    title,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                )
                IconButton(
                    onClick = { showInfoDialog = true },
                    modifier = Modifier
                        .offset(x = 2.dp)
                        .size(24.dp)
                        .padding(3.dp),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.baseline_info_24),
                        contentDescription = "Info",
                    )
                }
            }
        },
        text = {
            Column {
                if (restricted) {
                    Text(
                        text = "This setting is managed by your IT Admin.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                    )
                }
                content()
            }
        },
        confirmButton = {
            if (!restricted) {
                TextButton(onClick = onSave) {
                    Text("Save")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    if (restricted) {
                        "Close"
                    } else {
                        "Cancel"
                    }
                )
            }
        },
        properties = DialogProperties(
            dismissOnClickOutside = false,
        )
    )
}
