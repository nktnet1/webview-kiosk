package uk.nktnet.webviewkiosk.ui.components.setting

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import uk.nktnet.webviewkiosk.R
import uk.nktnet.webviewkiosk.ui.components.common.SystemSafeAlertDialog as AlertDialog
import uk.nktnet.webviewkiosk.utils.webview.HttpAuthRequest

@Composable
fun BasicAuthDialog(authRequest: HttpAuthRequest?, onDismiss: () -> Unit) {
    var username by remember(authRequest) { mutableStateOf("") }
    var password by remember(authRequest) { mutableStateOf("") }
    var showPassword by remember(authRequest) { mutableStateOf(false) }
    var requestCompleted by remember(authRequest) { mutableStateOf(false) }

    if (authRequest != null) {
        fun completeAuthentication(submit: Boolean) {
            if (requestCompleted) {
                return
            }
            requestCompleted = true
            val submittedUsername = username
            val submittedPassword = password
            username = ""
            password = ""
            showPassword = false
            if (submit) {
                authRequest.proceed(submittedUsername, submittedPassword)
            } else {
                authRequest.cancel()
            }
            onDismiss()
        }

        AlertDialog(
            onDismissRequest = { completeAuthentication(submit = false) },
            title = { Text(stringResource(R.string.ui_authentication_required)) },
            text = {
                Column {
                    Text(
                        stringResource(
                            R.string.ui_host_value,
                            authRequest.host.orEmpty(),
                        )
                    )
                    Text(
                        stringResource(
                            R.string.ui_realm_value,
                            authRequest.realm.orEmpty(),
                        )
                    )
                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        label = { Text(stringResource(R.string.ui_username)) },
                        singleLine = true,
                        keyboardOptions =
                            KeyboardOptions.Default.copy(imeAction = ImeAction.Next),
                        keyboardActions = KeyboardActions(),
                        modifier = Modifier.padding(top = 12.dp),
                    )
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text(stringResource(R.string.ui_password)) },
                        visualTransformation =
                            if (showPassword) {
                                VisualTransformation.None
                            } else {
                                PasswordVisualTransformation()
                            },
                        singleLine = true,
                        trailingIcon = {
                            IconButton(onClick = { showPassword = !showPassword }) {
                                Icon(
                                    painter =
                                        painterResource(
                                            if (showPassword) {
                                                R.drawable.outline_visibility_24
                                            } else {
                                                R.drawable.outline_visibility_off_24
                                            }
                                        ),
                                    contentDescription =
                                        if (showPassword) {
                                            stringResource(R.string.ui_hide_password)
                                        } else {
                                            stringResource(R.string.ui_show_password)
                                        },
                                )
                            }
                        },
                        keyboardOptions =
                            KeyboardOptions.Default.copy(imeAction = ImeAction.Done),
                        keyboardActions =
                            KeyboardActions(
                                onDone = {
                                    completeAuthentication(submit = true)
                                }
                            ),
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { completeAuthentication(submit = true) },
                    modifier = Modifier.width(95.dp),
                ) {
                    Text(stringResource(R.string.ui_login))
                }
            },
            dismissButton = {
                Button(
                    onClick = { completeAuthentication(submit = false) },
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError,
                        ),
                    modifier = Modifier.width(95.dp),
                ) {
                    Text(stringResource(R.string.ui_cancel))
                }
            },
        )
    }
}
