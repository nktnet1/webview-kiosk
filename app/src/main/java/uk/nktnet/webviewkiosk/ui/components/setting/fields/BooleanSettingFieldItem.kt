package com.nktnet.webview_kiosk.ui.components.setting.fields

import androidx.compose.runtime.*

@Composable
fun BooleanSettingFieldItem(
    label: String,
    infoText: String,
    initialValue: Boolean,
    settingKey: String,
    restricted: Boolean,
    onSave: (Boolean) -> Unit,
    itemText: (Boolean) -> String = { if (it) "True" else "False" },
    extraContent: (@Composable ((setValue: (Boolean) -> Unit) -> Unit))? = null,
) {
    DropdownSettingFieldItem(
        label = label,
        infoText = infoText,
        options = listOf(true, false),
        initialValue = initialValue,
        settingKey = settingKey,
        restricted = restricted,
        onSave = onSave,
        itemText = itemText,
        extraContent = extraContent,
    )
}
