package com.nktnet.webview_kiosk.ui.components.setting.fields

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import com.nktnet.webview_kiosk.ui.components.setting.dialog.GenericSettingFieldDialog

@Composable
fun CustomSettingFieldItem(
    label: String,
    infoText: String,
    value: String,
    settingKey: String,
    restricted: Boolean,
    onSave: () -> Unit,
    onDismissCallback: () -> Unit = {},
    bodyContent: @Composable () -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }

    GenericSettingFieldItem(
        label = label,
        value = value.ifBlank { "(blank)" },
        restricted = restricted,
        onClick = { showDialog = true }
    ) { displayValue ->
        Text(
            text = displayValue,
            style = if (value.isEmpty()) {
                MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic)
            } else {
                MaterialTheme.typography.bodyMedium
            },
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth()
        )
    }

    if (showDialog) {
        GenericSettingFieldDialog(
            title = label,
            infoText = infoText,
            restricted = restricted,
            onDismiss = {
                showDialog = false
                onDismissCallback()
            },
            settingKey = settingKey,
            onSave = {
                onSave()
                showDialog = false
            }
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                bodyContent()
            }
        }
    }
}
