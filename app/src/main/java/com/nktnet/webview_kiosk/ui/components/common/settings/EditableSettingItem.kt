package com.nktnet.webview_kiosk.ui.components.common.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material3.DividerDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun EditableSettingItem(
    label: String,
    infoText: String,
    placeholder: String,
    initialValue: String,
    isMultiline: Boolean,
    validator: (String) -> Boolean,
    onSave: (String) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }
    var value by remember { mutableStateOf(initialValue) }
    var draftValue by remember { mutableStateOf(initialValue) }
    var draftError by remember { mutableStateOf(false) }

    val description = if (isMultiline) {
        val combined = value.split("\n").joinToString(" | ")
        combined.ifBlank { "(blank)" }
    } else value.ifBlank { "(blank)" }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                draftValue = value
                draftError = false
                showDialog = true
            }
            .padding(top = 8.dp, start = 2.dp, end = 2.dp)
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.fillMaxWidth(0.9f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = description,
                    style = if (description == "(blank)") MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic)
                    else MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Icon(
                imageVector = Icons.Default.Edit,
                contentDescription = "Edit",
                modifier = Modifier
                    .align(Alignment.CenterEnd)
            )
        }
        HorizontalDivider(
            modifier = Modifier.padding(top = 8.dp),
            thickness = DividerDefaults.Thickness,
            color = DividerDefaults.color
        )
    }

    if (showDialog) {
        EditFieldDialog(
            title = label,
            infoText = infoText,
            placeholder = placeholder,
            value = draftValue,
            onValueChange = {
                draftValue = it
                draftError = !validator(it)
            },
            isMultiline = isMultiline,
            isError = draftError,
            onDismiss = { showDialog = false },
            onSave = {
                if (!draftError) {
                    value = draftValue
                    onSave(draftValue)
                    showDialog = false
                }
            }
        )
    }
}
