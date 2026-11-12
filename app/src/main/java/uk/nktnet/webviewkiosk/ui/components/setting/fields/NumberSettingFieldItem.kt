package uk.nktnet.webviewkiosk.ui.components.setting.fields

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import uk.nktnet.webviewkiosk.R
import uk.nktnet.webviewkiosk.ui.components.setting.dialog.GenericSettingFieldDialog
import uk.nktnet.webviewkiosk.ui.screens.settingDefinition

@Composable
fun NumberSettingFieldItem(
    settingKey: String,
    label: String = stringResource(settingDefinition(settingKey).titleRes),
    infoText: String = stringResource(settingDefinition(settingKey).descriptionRes),
    placeholder: String,
    initialValue: Int,
    restricted: Boolean,
    min: Int? = null,
    max: Int? = null,
    validationMessage: String? = null,
    descriptionFormatter: ((String) -> String)? = null,
    onSave: (Int) -> Unit,
    extraContent:
        (@Composable
        ((draftValue: String, setValue: (newValue: String) -> Unit) -> Unit))? =
        null,
) {
    var showDialog by remember { mutableStateOf(false) }
    var value by remember { mutableIntStateOf(initialValue) }
    var draftValue by remember { mutableStateOf(initialValue.toString()) }
    var draftError by remember { mutableStateOf(false) }

    fun validateNumber(input: String): Boolean {
        val number =
            if (input.isEmpty()) {
                0
            } else {
                val parsed = input.toLongOrNull() ?: return false
                if (parsed > Int.MAX_VALUE || parsed < Int.MIN_VALUE) {
                    return false
                }
                parsed.toInt()
            }
        return (number == 0 ||
            ((min == null || number >= min) && (max == null || number <= max)))
    }

    GenericSettingFieldItem(
        settingKey = settingKey,
        label = label,
        value = value.toString(),
        restricted = restricted,
        onClick = {
            draftValue = value.toString()
            draftError = !validateNumber(value.toString())
            showDialog = true
        },
    ) { v ->
        Text(
            text =
                if (descriptionFormatter != null) {
                    descriptionFormatter(v)
                } else if (v == "0") {
                    stringResource(R.string.ui_0_disabled)
                } else {
                    v
                },
            style =
                MaterialTheme.typography.bodyMedium.copy(
                    fontStyle =
                        if (v == "0" && descriptionFormatter == null) {
                            FontStyle.Italic
                        } else {
                            FontStyle.Normal
                        }
                ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }

    if (showDialog) {
        GenericSettingFieldDialog(
            title = label,
            infoText = infoText,
            settingKey = settingKey,
            restricted = restricted,
            onDismiss = { showDialog = false },
            onSave = {
                if (!draftError) {
                    val number =
                        if (draftValue.isEmpty()) {
                            0
                        } else {
                            draftValue.toIntOrNull() ?: 0
                        }
                    value = number
                    onSave(number)
                    showDialog = false
                }
            },
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                TextField(
                    enabled = !restricted,
                    value = draftValue,
                    onValueChange = {
                        draftValue = it.filter { ch -> ch.isDigit() }
                        draftError = !validateNumber(draftValue)
                    },
                    isError = draftError,
                    singleLine = true,
                    placeholder = {
                        Text(placeholder, fontStyle = FontStyle.Italic)
                    },
                    trailingIcon = {
                        if (restricted) {
                            return@TextField
                        }
                        IconButton(
                            onClick = {
                                draftValue = ""
                                draftError = !validateNumber("")
                            },
                            enabled = draftValue.isNotEmpty(),
                        ) {
                            Icon(
                                painter =
                                    painterResource(R.drawable.baseline_clear_24),
                                contentDescription =
                                    stringResource(R.string.ui_clear),
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (draftError) {
                    Text(
                        text =
                            validationMessage
                                ?: run {
                                    when {
                                        min != null && max != null ->
                                            stringResource(
                                                R.string
                                                    .ui_enter_a_number_between_value_and_value,
                                                min,
                                                max,
                                            )
                                        min != null ->
                                            stringResource(
                                                R.string
                                                    .ui_enter_a_number_value_or_0_to_disable,
                                                min,
                                            )
                                        max != null ->
                                            stringResource(
                                                R.string
                                                    .ui_enter_a_number_value_or_0_to_disable_2,
                                                max,
                                            )
                                        else ->
                                            stringResource(
                                                R.string.ui_invalid_number
                                            )
                                    }
                                },
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                }
                extraContent?.invoke(draftValue) { newValue ->
                    draftValue = newValue
                    draftError = !validateNumber(newValue)
                }
            }
        }
    }
}
