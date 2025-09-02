package com.nktnet.webview_kiosk.ui.components.common.settings.fields

import DropdownSelector
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.nktnet.webview_kiosk.ui.components.common.settings.dialog.GenericSettingFieldDialog

@Composable
fun BooleanSettingFieldItem(
    label: String,
    infoText: String,
    initialValue: Boolean,
    onSave: (Boolean) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }
    var value by remember { mutableStateOf(initialValue) }
    var draftValue by remember { mutableStateOf(initialValue) }

    val options = listOf("Enabled", "Disabled")

    GenericSettingFieldItem(
        label = label,
        value = if (value) "Enabled" else "Disabled",
        onClick = {
            draftValue = value
            showDialog = true
        }
    ) { v ->
        val description = if (v == "Enabled") "Enabled" else "Disabled"
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth()
        )
    }

    if (showDialog) {
        GenericSettingFieldDialog(
            title = label,
            infoText = infoText,
            onDismiss = { showDialog = false },
            onSave = {
                value = draftValue
                onSave(draftValue)
                showDialog = false
            }
        ) {
            DropdownSelector(
                options = options,
                selected = if (draftValue) "Enabled" else "Disabled",
                onSelectedChange = { selected ->
                    draftValue = selected == "Enabled"
                }
            ) { option ->
                Text(
                    text = option,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
