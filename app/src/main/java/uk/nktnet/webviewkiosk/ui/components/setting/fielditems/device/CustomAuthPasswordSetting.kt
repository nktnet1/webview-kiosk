package uk.nktnet.webviewkiosk.ui.components.setting.fielditems.device

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import uk.nktnet.webviewkiosk.R
import uk.nktnet.webviewkiosk.config.Constants
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.UserSettingsKeys
import uk.nktnet.webviewkiosk.ui.components.common.SystemSafeAlertDialog as AlertDialog
import uk.nktnet.webviewkiosk.ui.components.setting.fields.TextSettingFieldItem
import uk.nktnet.webviewkiosk.ui.screens.settingDefinition
import uk.nktnet.webviewkiosk.utils.normaliseInfoText
import uk.nktnet.webviewkiosk.utils.requestFocusWhenPlaced

@Composable
fun CustomAuthPasswordSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }
    val settingKey = UserSettingsKeys.Device.CUSTOM_AUTH_PASSWORD

    val restricted = userSettings.isRestricted(settingKey)
    val maxCharacters = 128

    var confirmPassword by remember { mutableStateOf("") }
    var showConfirmationDialog by remember { mutableStateOf(false) }

    var showRemovePasswordDialog by remember { mutableStateOf(false) }

    var pendingPassword by remember { mutableStateOf("") }
    var pendingCommit by remember { mutableStateOf<(() -> Unit)?>(null) }
    var pendingRemoveCommit by remember { mutableStateOf<(() -> Unit)?>(null) }

    var showPassword by remember { mutableStateOf(false) }
    var passwordMismatch by remember { mutableStateOf(false) }

    val focusRequester = remember { FocusRequester() }

    val blankCredentialsLabel = stringResource(R.string.ui_blank_device_credentials)

    TextSettingFieldItem(
        infoText =
            stringResource(
                settingDefinition(settingKey).descriptionRes,
                Constants.DOCUMENTATION_URL,
            ),
        placeholder = stringResource(R.string.ui_blank_device_credentials),
        initialValue = userSettings.customAuthPassword,
        settingKey = settingKey,
        restricted = restricted,
        isMultiline = false,
        isPassword = true,
        validator = {
            it.length <= maxCharacters
        },
        validationMessage =
            stringResource(
                R.string.ui_please_enter_fewer_than_value_characters,
                maxCharacters,
            ),
        descriptionFormatter = { value ->
            if (value.isNotBlank()) {
                "*".repeat(20)
            } else {
                blankCredentialsLabel
            }
        },
        saveText = stringResource(R.string.ui_next),
        onSaveDeferred = { password, commit ->
            if (password.isBlank()) {
                pendingRemoveCommit = commit
                showRemovePasswordDialog = true
            } else {
                pendingPassword = password
                pendingCommit = commit
                confirmPassword = ""
                showPassword = false
                passwordMismatch = false
                showConfirmationDialog = true
            }
        },
    )

    if (showConfirmationDialog) {
        AlertDialog(
            onDismissRequest = {
                showConfirmationDialog = false
                pendingCommit = null
                passwordMismatch = false
            },
            title = {
                Text(
                    style = MaterialTheme.typography.titleMedium,
                    text = stringResource(R.string.ui_re_enter_password),
                )
            },
            text = {
                Column {
                    OutlinedTextField(
                        value = confirmPassword,
                        onValueChange = {
                            confirmPassword = it
                            passwordMismatch = false
                        },
                        label = {
                            Text(stringResource(R.string.ui_confirm_password))
                        },
                        singleLine = true,
                        visualTransformation =
                            if (showPassword) {
                                VisualTransformation.None
                            } else {
                                PasswordVisualTransformation()
                            },
                        trailingIcon = {
                            IconButton(
                                onClick = {
                                    confirmPassword = ""
                                },
                                enabled = confirmPassword.isNotEmpty(),
                            ) {
                                Icon(
                                    painter =
                                        painterResource(
                                            R.drawable.baseline_clear_24
                                        ),
                                    contentDescription =
                                        stringResource(R.string.ui_clear),
                                )
                            }
                        },
                        modifier =
                            Modifier.requestFocusWhenPlaced(
                                focusRequester = focusRequester,
                                enabled = showConfirmationDialog,
                                requestKey = showConfirmationDialog,
                            ),
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = showPassword,
                            onCheckedChange = {
                                showPassword = it
                            },
                        )

                        Text(
                            text = stringResource(R.string.ui_show_password),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier =
                                Modifier.combinedClickable(
                                    interactionSource =
                                        remember {
                                            MutableInteractionSource()
                                        },
                                    indication = null,
                                    onClick = {
                                        showPassword = !showPassword
                                    },
                                ),
                        )
                    }

                    if (passwordMismatch) {
                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text =
                                stringResource(R.string.ui_passwords_do_not_match),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (confirmPassword == pendingPassword) {
                            userSettings.customAuthPassword = pendingPassword
                            pendingCommit?.invoke()

                            pendingCommit = null
                            showConfirmationDialog = false
                        } else {
                            passwordMismatch = true
                        }
                    }
                ) {
                    Text(stringResource(R.string.ui_confirm))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showConfirmationDialog = false
                        pendingCommit = null
                        passwordMismatch = false
                    }
                ) {
                    Text(stringResource(R.string.ui_cancel))
                }
            },
        )
    }

    if (showRemovePasswordDialog) {
        AlertDialog(
            onDismissRequest = {
                showRemovePasswordDialog = false
                pendingRemoveCommit = null
            },
            title = {
                Text(
                    style = MaterialTheme.typography.titleMedium,
                    text = stringResource(R.string.ui_remove_custom_password),
                )
            },
            text = {
                Text(
                    normaliseInfoText(
                        stringResource(
                            R.string.ui_remove_custom_password_confirmation
                        )
                    )
                )
            },
            confirmButton = {
                TextButton(
                    colors =
                        ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                    onClick = {
                        userSettings.customAuthPassword = ""
                        pendingRemoveCommit?.invoke()
                        pendingRemoveCommit = null
                        showRemovePasswordDialog = false
                    },
                ) {
                    Text(stringResource(R.string.ui_remove))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showRemovePasswordDialog = false
                        pendingRemoveCommit = null
                    }
                ) {
                    Text(stringResource(R.string.ui_cancel))
                }
            },
        )
    }
}
