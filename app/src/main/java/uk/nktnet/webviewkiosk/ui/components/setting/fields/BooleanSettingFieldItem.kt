package uk.nktnet.webviewkiosk.ui.components.setting.fields

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import uk.nktnet.webviewkiosk.R
import uk.nktnet.webviewkiosk.ui.screens.settingDefinition

@Composable
fun BooleanSettingFieldItem(
    settingKey: String,
    label: String = stringResource(settingDefinition(settingKey).titleRes),
    infoText: String = stringResource(settingDefinition(settingKey).descriptionRes),
    initialValue: Boolean,
    restricted: Boolean,
    onSave: (Boolean) -> Unit,
    disabledReason: String? = null,
    itemText: (@Composable (Boolean) -> String)? = null,
    extraContent: (@Composable ((setValue: (Boolean) -> Unit) -> Unit))? = null,
) {
    val resolvedItemText: @Composable (Boolean) -> String =
        itemText
            ?: { value: Boolean ->
                stringResource(
                    if (value) {
                        R.string.ui_true
                    } else {
                        R.string.ui_false
                    }
                )
            }
    DropdownSettingFieldItem(
        label = label,
        infoText = infoText,
        options = listOf(true, false),
        initialValue = initialValue,
        settingKey = settingKey,
        restricted = restricted,
        disabledReason = disabledReason,
        onSave = onSave,
        itemText = resolvedItemText,
        extraContent = extraContent,
    )
}
