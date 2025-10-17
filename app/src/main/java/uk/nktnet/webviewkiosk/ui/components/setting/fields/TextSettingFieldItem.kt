package com.nktnet.webview_kiosk.ui.components.setting.fields

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nktnet.webview_kiosk.R
import com.nktnet.webview_kiosk.ui.components.setting.dialog.GenericSettingFieldDialog

@Composable
fun TextSettingFieldItem(
    label: String,
    infoText: String,
    placeholder: String,
    initialValue: String,
    isMultiline: Boolean,
    modifier: Modifier = Modifier,
    restricted: Boolean,
    validator: (String) -> Boolean = { true },
    validationMessage: String? = null,
    onSave: (String) -> Unit,
    readOnly: Boolean = false,
    extraContent: (@Composable ((setValue: (String) -> Unit) -> Unit))? = null,
) {
    var showDialog by remember { mutableStateOf(false) }
    var value by remember { mutableStateOf(initialValue) }
    var draftValue by remember { mutableStateOf(initialValue) }
    var draftError by remember { mutableStateOf(false) }

    GenericSettingFieldItem(
        label = label,
        value = value,
        restricted = restricted,
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
            restricted = restricted,
            onDismiss = { showDialog = false },
            onSave = {
                if (!draftError) {
                    value = draftValue
                    onSave(draftValue)
                    showDialog = false
                }
            }
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = draftValue,
                    enabled = !restricted,
                    readOnly = readOnly,
                    onValueChange = {
                        draftValue = it
                        draftError = !validator(it)
                    },
                    isError = draftError,
                    placeholder = {
                        Text(
                            placeholder,
                            style = MaterialTheme.typography.bodyMedium,
                            fontStyle = FontStyle.Italic,
                            maxLines = if (isMultiline) Int.MAX_VALUE else 1,
                            overflow = if (isMultiline) TextOverflow.Visible else TextOverflow.Ellipsis
                        )
                    },
                    singleLine = !isMultiline,
                    modifier = if (!isMultiline) {
                        modifier
                            .fillMaxWidth()
                    } else {
                        modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 200.dp)
                            .heightIn(max = 400.dp)
                    },
                    trailingIcon = if (!isMultiline && !restricted) {
                        {
                            IconButton(
                                onClick = {
                                    draftValue = ""
                                    draftError = !validator("")
                                },
                                enabled = draftValue.isNotEmpty()
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.baseline_clear_24),
                                    contentDescription = "Clear"
                                )
                            }
                        }
                    } else null
                )

                if (isMultiline && !restricted) {
                    IconButton(
                        onClick = {
                            draftValue = ""
                            draftError = !validator("")
                        },
                        enabled = draftValue.isNotEmpty(),
                        modifier = Modifier
                            .padding(top = 4.dp)
                            .align(Alignment.End)
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.baseline_clear_24),
                            contentDescription = "Clear"
                        )
                    }
                }

                if (draftError) {
                    Text(
                        text = validationMessage ?: "Invalid input",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    )
                }
                extraContent?.invoke { draftValue = it; draftError = !validator(it) }
            }
        }
    }
}
