package uk.nktnet.webviewkiosk.ui.components.setting.fields

import uk.nktnet.webviewkiosk.ui.components.common.DropdownSelector
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import uk.nktnet.webviewkiosk.ui.components.setting.dialog.GenericSettingFieldDialog

@Composable
fun <T> DropdownSettingFieldItem(
    label: String,
    infoText: String,
    options: List<T>,
    initialValue: T,
    restricted: Boolean,
    extraContent: (@Composable ((setValue: (T) -> Unit) -> Unit))? = null,
    onSave: (T) -> Unit,
    itemText: (T) -> String
) {
    var showDialog by remember { mutableStateOf(false) }
    var value by remember { mutableStateOf(initialValue) }
    var draftValue by remember { mutableStateOf(initialValue) }

    GenericSettingFieldItem(
        label = label,
        value = itemText(value),
        restricted = restricted,
        onClick = {
            draftValue = value
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
            restricted = restricted,
            onSave = {
                value = draftValue
                onSave(draftValue)
                showDialog = false
            }
        ) {
            DropdownSelector(
                options = options,
                selected = draftValue,
                enabled = !restricted,
                onSelectedChange = { draftValue = it }
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
            extraContent?.invoke { draftValue = it }
        }
    }
}
