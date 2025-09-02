package com.nktnet.webview_kiosk.ui.components.common.settings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun EditFieldDialog(
    title: String,
    infoText: String,
    placeholder: String,
    value: String,
    onValueChange: (String) -> Unit,
    isMultiline: Boolean = false,
    isError: Boolean = false,
    onDismiss: () -> Unit,
    onSave: () -> Unit
) {
    GenericSettingFieldDialog(
        title = title,
        infoText = infoText,
        onDismiss = onDismiss,
        onSave = onSave
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            isError = isError,
            placeholder = { Text(placeholder) },
            singleLine = !isMultiline,
            modifier = if (isMultiline) Modifier.height(200.dp) else Modifier.fillMaxWidth()
        )
    }
}
