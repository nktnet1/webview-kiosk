package uk.nktnet.webviewkiosk.ui.components.setting.fields

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import uk.nktnet.webviewkiosk.ui.components.setting.dialog.GenericSettingFieldDialog

@Composable
fun TextSettingFieldItem(
    label: String,
    infoText: String,
    placeholder: String,
    initialValue: String,
    isMultiline: Boolean,
    validator: (String) -> Boolean,
    onSave: (String) -> Unit,
    extraContent: (@Composable ((setValue: (String) -> Unit) -> Unit))? = null
) {
    var showDialog by remember { mutableStateOf(false) }
    var value by remember { mutableStateOf(initialValue) }
    var draftValue by remember { mutableStateOf(initialValue) }
    var draftError by remember { mutableStateOf(false) }

    GenericSettingFieldItem(
        label = label,
        value = value,
        onClick = {
            draftValue = value
            draftError = false
            showDialog = true
        }
    ) { v ->
        val description = if (isMultiline) {
            v.split("\n").joinToString(" | ").ifBlank { "(blank)" }
        } else v.ifBlank { "(blank)" }

        Text(
            text = description,
            style = if (description == "(blank)")
                MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic)
            else
                MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth()
        )
    }

    if (showDialog) {
        GenericSettingFieldDialog(
            title = label,
            infoText = infoText,
            onDismiss = { showDialog = false },
            onSave = {
                if (!draftError) {
                    value = draftValue
                    onSave(draftValue)
                    showDialog = false
                }
            }
        ) {
            OutlinedTextField(
                value = draftValue,
                onValueChange = {
                    draftValue = it
                    draftError = !validator(it)
                },
                isError = draftError,
                placeholder = {
                    if (isMultiline) {
                        Text(
                            placeholder,
                            style = MaterialTheme.typography.bodyMedium,
                            fontStyle = FontStyle.Italic,
                        )
                    } else {
                        Text(
                            placeholder,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontStyle = FontStyle.Italic,
                        )
                    }
                },
                singleLine = !isMultiline,
                modifier = if (isMultiline) {
                    Modifier
                        .height(200.dp)
                        .fillMaxWidth()
                } else {
                    Modifier.fillMaxWidth()
                }
            )

            extraContent?.invoke { draftValue = it; draftError = !validator(it) } }
    }
}
