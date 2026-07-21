package uk.nktnet.webviewkiosk.ui.components.setting.fielditems.device

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.android.awaitFrame
import uk.nktnet.webviewkiosk.R
import uk.nktnet.webviewkiosk.config.Constants
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.UserSettingsKeys
import uk.nktnet.webviewkiosk.ui.components.setting.fields.TextSettingFieldItem
import uk.nktnet.webviewkiosk.utils.normaliseInfoText

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

    LaunchedEffect(showConfirmationDialog) {
        if (showConfirmationDialog) {
            awaitFrame()

            runCatching {
                focusRequester.requestFocus()
            }
        }
    }

    TextSettingFieldItem(
        label = stringResource(R.string.device_custom_auth_password_title),
        infoText = """
            Specify a custom password to protect your settings or when unlocking from
            the kiosk state.

            For user-owned devices that utilises screen pinning, this will only work
            if you are using an unlock method provided by the app.

            Device-level unpin methods (e.g. gestures/holding overview + back button
            simultaneously) will bypass this setting. To enhance security, please
            see: ${Constants.DOCUMENTATION_URL}/security

            Leave this setting blank to use your device's biometrics or credentials.
        """.trimIndent(),
        placeholder = "(blank = device credentials)",
        initialValue = userSettings.customAuthPassword,
        settingKey = settingKey,
        restricted = restricted,
        isMultiline = false,
        isPassword = true,
        validator = {
            it.length <= maxCharacters
        },
        validationMessage = "Please enter fewer than $maxCharacters characters.",
        descriptionFormatter = { value ->
            if (value.isNotBlank()) {
                "*".repeat(20)
            } else {
                "(blank = device credentials)"
            }
        },
        saveText = "Next",
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
        }
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
                    text = "Re-enter password"
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
                            Text("Confirm password")
                        },
                        singleLine = true,
                        visualTransformation = if (showPassword) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                        trailingIcon = {
                            IconButton(
                                onClick = {
                                    confirmPassword = ""
                                },
                                enabled = confirmPassword.isNotEmpty()
                            ) {
                                Icon(
                                    painter = painterResource(
                                        R.drawable.baseline_clear_24
                                    ),
                                    contentDescription = "Clear"
                                )
                            }
                        },
                        modifier = Modifier
                            .focusRequester(focusRequester)
                    )

                    Spacer(
                        modifier = Modifier.height(2.dp)
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = showPassword,
                            onCheckedChange = {
                                showPassword = it
                            }
                        )

                        Text(
                            text = "Show password",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier
                                .combinedClickable(
                                    interactionSource = remember {
                                        MutableInteractionSource()
                                    },
                                    indication = null,
                                    onClick = {
                                        showPassword = !showPassword
                                    }
                                )
                        )
                    }

                    if (passwordMismatch) {
                        Spacer(
                            modifier = Modifier.height(4.dp)
                        )

                        Text(
                            text = "Passwords do not match.",
                            color = MaterialTheme.colorScheme.error
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
                    Text("Confirm")
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
                    Text("Cancel")
                }
            }
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
                    text = "Remove Custom Password"
                )
            },
            text = {
                Text(
                    normaliseInfoText(
                        """
                        Are you sure you want to remove your custom password?

                        Your device credentials or biometrics will be used instead.
                        """.trimIndent()
                    )
                )
            },
            confirmButton = {
                TextButton(
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                    onClick = {
                        userSettings.customAuthPassword = ""
                        pendingRemoveCommit?.invoke()
                        pendingRemoveCommit = null
                        showRemovePasswordDialog = false
                    }
                ) {
                    Text("Remove")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showRemovePasswordDialog = false
                        pendingRemoveCommit = null
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}
