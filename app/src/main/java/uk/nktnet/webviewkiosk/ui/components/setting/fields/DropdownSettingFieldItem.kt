package uk.nktnet.webviewkiosk.ui.components.setting.fields

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import uk.nktnet.webviewkiosk.R
import uk.nktnet.webviewkiosk.ui.components.common.DropdownSelector
import uk.nktnet.webviewkiosk.ui.components.setting.dialog.GenericSettingFieldDialog
import uk.nktnet.webviewkiosk.ui.screens.settingDefinition

@Composable
fun <T> DropdownSettingFieldItem(
    settingKey: String,
    label: String = stringResource(settingDefinition(settingKey).titleRes),
    infoText: String = stringResource(settingDefinition(settingKey).descriptionRes),
    options: List<T>,
    initialValue: T,
    restricted: Boolean,
    disabledReason: String? = null,
    extraContent: (@Composable ((setValue: (T) -> Unit) -> Unit))? = null,
    extraContentForValue: (@Composable (T) -> Unit)? = null,
    validator: (value: T) -> Boolean = { true },
    validationMessage: String? = null,
    onSave: (T) -> Unit,
    itemText: @Composable (T) -> String,
) {
    val canEdit = !restricted && disabledReason == null
    var showDialog by remember { mutableStateOf(false) }
    var value by remember { mutableStateOf(initialValue) }
    var draftValue by remember { mutableStateOf(initialValue) }
    var draftError by remember { mutableStateOf(false) }

    GenericSettingFieldItem(
        settingKey = settingKey,
        label = label,
        value = itemText(value),
        restricted = restricted,
        disabledReason = disabledReason,
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
            modifier = Modifier.fillMaxWidth(),
        )
    }

    if (showDialog) {
        GenericSettingFieldDialog(
            title = label,
            infoText = infoText,
            onDismiss = { showDialog = false },
            settingKey = settingKey,
            restricted = restricted,
            disabledReason = disabledReason,
            onSave = {
                if (canEdit && !draftError) {
                    value = draftValue
                    onSave(draftValue)
                    showDialog = false
                }
            },
        ) {
            DropdownSelector(
                options = options,
                selected = draftValue,
                enabled = canEdit,
                onSelectedChange = {
                    if (canEdit) {
                        draftValue = it
                        draftError = !validator(it)
                    }
                },
            ) { option ->
                val isSelected = option == draftValue
                Text(
                    text = itemText(option),
                    style = MaterialTheme.typography.bodyMedium,
                    color =
                        if (restricted) {
                            MaterialTheme.colorScheme.error
                        } else if (isSelected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                )
            }
            if (draftError) {
                Text(
                    text =
                        validationMessage
                            ?: stringResource(R.string.ui_invalid_input),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            }
            extraContentForValue?.invoke(draftValue)
            extraContent?.invoke {
                if (canEdit) {
                    draftValue = it
                }
            }
        }
    }
}
