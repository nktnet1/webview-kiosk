package com.nktnet.webview_kiosk.ui.components.common.settings

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun EditBooleanFieldDialog(
    title: String,
    infoText: String,
    value: Boolean,
    onValueChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit
) {
    val currentValue = remember { mutableStateOf(value) }

    GenericSettingFieldDialog(
        title = title,
        infoText = infoText,
        onDismiss = onDismiss,
        onSave = {
            onValueChange(currentValue.value)
            onSave()
        }
    ) {
        Row {
            Text(text = if (currentValue.value) "Enabled" else "Disabled")
            Spacer(modifier = Modifier.width(16.dp))
            Switch(
                checked = currentValue.value,
                onCheckedChange = { currentValue.value = it }
            )
        }
    }
}
