package com.nktnet.webview_kiosk.ui.components.setting.fields

import com.nktnet.webview_kiosk.ui.components.common.DropdownSelector
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nktnet.webview_kiosk.ui.components.setting.dialog.GenericSettingFieldDialog

@Composable
fun <T> DropdownSettingFieldItem(
    label: String,
    infoText: String,
    options: List<T>,
    initialValue: T,
    settingKey: String,
    restricted: Boolean,
    extraContent: (@Composable ((setValue: (T) -> Unit) -> Unit))? = null,
    validator: (value: T) -> Boolean = { true },
    validationMessage: String? = null,
    onSave: (T) -> Unit,
    itemText: (T) -> String
) {
    var showDialog by remember { mutableStateOf(false) }
    var value by remember { mutableStateOf(initialValue) }
    var draftValue by remember { mutableStateOf(initialValue) }
    var draftError by remember { mutableStateOf(false) }

    GenericSettingFieldItem(
        label = label,
        value = itemText(value),
        restricted = restricted,
        onClick = {
            draftValue = value
            draftError = !validator(value)
            showDialog = true
        },
    ) {
        Text(
            text = itemText(value),
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
            settingKey = settingKey,
            restricted = restricted,
            onSave = {
                if (!draftError) {
                    value = draftValue
                    onSave(draftValue)
                    showDialog = false
                }
            }
        ) {
            DropdownSelector(
                options = options,
                selected = draftValue,
                enabled = !restricted,
                onSelectedChange = {
                    draftValue = it
                    draftError = !validator(it)
                }
            ) { option ->
                val isSelected = option == draftValue
                Text(
                    text = itemText(option),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (restricted) {
                        MaterialTheme.colorScheme.error
                    } else if (isSelected) {
                            MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
            if (draftError) {
                Text(
                    text = validationMessage ?: "Invalid input",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                )
            }
            extraContent?.invoke { draftValue = it }
        }
    }
}
