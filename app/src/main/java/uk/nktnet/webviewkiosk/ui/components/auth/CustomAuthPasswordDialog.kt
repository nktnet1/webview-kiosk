package com.nktnet.webview_kiosk.ui.components.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.nktnet.webview_kiosk.R
import com.nktnet.webview_kiosk.managers.AuthenticationManager
import com.nktnet.webview_kiosk.config.UserSettings
import com.nktnet.webview_kiosk.managers.ToastManager

@Composable
fun CustomAuthPasswordDialog() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }

    val state by AuthenticationManager.promptResults.collectAsState()

    if (
        !AuthenticationManager.showCustomAuth.value
        || state != AuthenticationManager.AuthenticationResult.Loading
    ) {
        return
    }

    val focusRequester = remember { FocusRequester() }
    var password by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    var waiting by remember { mutableStateOf(false) }
    var isError by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(100)
        focusRequester.requestFocus()
    }

    fun handleUnlock() {
        scope.launch {
            waiting = true
            val start = System.currentTimeMillis()
            if (password == userSettings.customAuthPassword) {
                password = ""
                AuthenticationManager.customAuthSuccess()
            } else {
                val elapsed = System.currentTimeMillis() - start
                val remaining = 1000L - elapsed
                if (remaining > 0) {
                    delay(remaining)
                }
                isError = true
                ToastManager.show(context, "Incorrect password")
            }
            waiting = false
        }
    }

    Dialog(
        onDismissRequest = { AuthenticationManager.customAuthCancel() }
    ) {
        Box(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.background.copy(alpha = 0.95f))
                .verticalScroll(rememberScrollState()),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .padding(32.dp)
                    .fillMaxWidth()
            ) {
                Spacer(modifier = Modifier.height(4.dp))

                val isNumeric = userSettings.customAuthPassword.all { it.isDigit() }

                OutlinedTextField(
                    value = password,
                    onValueChange = {
                        if (!waiting) {
                            password = it
                            isError = false
                        }
                    },
                    isError = isError,
                    label = { Text("Password") },
                    placeholder = { Text("Enter your password") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = if (isNumeric) {
                        KeyboardOptions(
                            keyboardType = KeyboardType.NumberPassword,
                            imeAction = ImeAction.Done
                        )
                    } else {
                        KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done
                        )
                    },
                    keyboardActions = KeyboardActions(
                        onDone = { handleUnlock() },
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                    singleLine = true,
                    trailingIcon = {
                        IconButton(
                            onClick = {
                                password = ""
                            },
                            enabled = password.isNotEmpty() && !waiting
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.baseline_clear_24),
                                contentDescription = "Clear"
                            )
                        }
                    }
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextButton(
                        onClick = {
                            password = ""
                            AuthenticationManager.customAuthCancel()
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondary,
                        ),
                        enabled = !waiting
                    ) {
                        Text("Cancel")
                    }

                    Button(
                        onClick = { handleUnlock() },
                        modifier = Modifier.weight(1f),
                        enabled = !waiting
                    ) {
                        Text("Unlock")
                    }
                }
            }
        }
    }
}
