package com.nktnet.webview_kiosk.ui.components.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
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
            thickness = DividerDefaults.Thickness,
            color = DividerDefaults.color,
            modifier = Modifier.padding(top = 8.dp)
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
    var showInfoDialog by remember { mutableStateOf(false) }

    if (showInfoDialog) {
        AlertDialog(
            onDismissRequest = { showInfoDialog = false },
            title = { Text("Info") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(infoText.replace("\t", "    "))
                }
            },
            confirmButton = {
                TextButton(onClick = { showInfoDialog = false }) { Text("OK") }
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, modifier = Modifier.weight(1f))
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = "Info",
                    modifier = Modifier
                        .size(20.dp)
                        .clickable { showInfoDialog = true }
                        .align(Alignment.CenterVertically)
                )
            }
        },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                isError = isError,
                placeholder = { Text(placeholder) },
                singleLine = !isMultiline,
                modifier = if (isMultiline) Modifier.height(200.dp) else Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = onSave) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
