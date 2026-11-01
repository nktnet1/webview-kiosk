package uk.nktnet.webviewkiosk.ui.components.setting.fields

import androidx.compose.runtime.Composable
import uk.nktnet.webviewkiosk.R

@Composable
fun BooleanSettingFieldItem(
    label: String,
    infoText: String,
    initialValue: Boolean,
    settingKey: String,
    restricted: Boolean,
    onSave: (Boolean) -> Unit,
    itemText: ((Boolean) -> String)? = null,
    extraContent: (@Composable ((setValue: (Boolean) -> Unit) -> Unit))? = null,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val resolvedItemText =
        itemText
            ?: { value: Boolean ->
                context.getString(
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
        onSave = onSave,
        itemText = resolvedItemText,
        extraContent = extraContent,
    )
}
