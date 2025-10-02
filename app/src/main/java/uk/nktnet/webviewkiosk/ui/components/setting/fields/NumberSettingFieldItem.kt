package uk.nktnet.webviewkiosk.ui.components.setting.fields

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import uk.nktnet.webviewkiosk.ui.components.setting.dialog.GenericSettingFieldDialog

@Composable
fun NumberSettingFieldItem(
    label: String,
    infoText: String,
    placeholder: String,
    initialValue: Int,
    min: Int? = null,
    max: Int? = null,
    validationMessage: String? = null,
    onSave: (Int) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }
    var value by remember { mutableIntStateOf(initialValue) }
    var draftValue by remember { mutableStateOf(initialValue.toString()) }
    var draftError by remember { mutableStateOf(false) }

    fun validateNumber(input: String): Boolean {
        val number = if (input.isEmpty()) 0 else input.toIntOrNull() ?: 0
        return number == 0 || ((min == null || number >= min) && (max == null || number <= max))
    }

    GenericSettingFieldItem(
        label = label,
        value = value.toString(),
        onClick = {
            draftValue = value.toString()
            draftError = false
            showDialog = true
        },
    ) { v ->
        Text(
            text = if (v == "0") "0 (disabled)" else v,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontStyle = if (v == "0") FontStyle.Italic else FontStyle.Normal
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
    }

    if (showDialog) {
        GenericSettingFieldDialog(
            title = label,
            infoText = infoText,
            onDismiss = { showDialog = false },
            onSave = {
                if (!draftError) {
                    val number = if (draftValue.isEmpty()) 0 else draftValue.toIntOrNull() ?: 0
                    value = number
                    onSave(number)
                    showDialog = false
                }
            }
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                TextField(
                    value = draftValue,
                    onValueChange = {
                        draftValue = it.filter { ch -> ch.isDigit() }
                        draftError = !validateNumber(draftValue)
                    },
                    isError = draftError,
                    singleLine = true,
                    placeholder = { Text(placeholder, fontStyle = FontStyle.Italic) },
                    trailingIcon = {
                        IconButton(
                            onClick = { draftValue = ""; draftError = !validateNumber("") },
                            enabled = draftValue.isNotEmpty()
                        ) {
                            Icon(imageVector = Icons.Default.Clear, contentDescription = "Clear")
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                if (draftError) {
                    Text(
                        text = validationMessage ?: run {
                            when {
                                min != null && max != null -> "Enter a number between $min and $max"
                                min != null -> "Enter a number ≥ $min (or 0 to disable)"
                                max != null -> "Enter a number ≤ $max (or 0 to disable)"
                                else -> "Invalid number"
                            }
                        },
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    )
                }
            }
        }
    }
}
