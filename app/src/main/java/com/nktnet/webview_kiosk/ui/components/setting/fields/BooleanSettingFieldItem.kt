package com.nktnet.webview_kiosk.ui.components.setting.fields

import androidx.compose.runtime.*

@Composable
fun BooleanSettingFieldItem(
    label: String,
    infoText: String,
    initialValue: Boolean,
    onSave: (Boolean) -> Unit
) {
    DropdownSettingFieldItem(
        label = label,
        infoText = infoText,
        options = listOf(true, false),
        initialValue = initialValue,
        onSave = onSave,
        itemText = { if (it) "Enabled" else "Disabled" }
    )
}