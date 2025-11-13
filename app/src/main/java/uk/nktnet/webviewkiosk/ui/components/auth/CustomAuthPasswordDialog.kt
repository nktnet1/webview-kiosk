package uk.nktnet.webviewkiosk.ui.components.auth

import android.widget.Toast
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import uk.nktnet.webviewkiosk.auth.AuthenticationManager
import uk.nktnet.webviewkiosk.config.UserSettings

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

    var toastRef: Toast? = null
    val showToast: (String) -> Unit = { msg ->
        toastRef?.cancel()
        toastRef = Toast.makeText(
            context, msg, Toast.LENGTH_SHORT
        ).apply { show() }
    }

    LaunchedEffect(Unit) {
        delay(100)
        focusRequester.requestFocus()
    }

    fun handleSuccess() {
        password = ""
        AuthenticationManager.customAuthSuccess()
    }

    fun handleFailure() {
        scope.launch {
            waiting = true
            password = ""
            delay(500)
            isError = true
            showToast("Incorrect password")
            waiting = false
        }
    }

    Dialog(
        onDismissRequest = { AuthenticationManager.customAuthCancel() }
    ) {
        Box(
            modifier = Modifier
                .background(Color.Black.copy(alpha = 0.8f))
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
                        password = it
                        isError = false
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
                        KeyboardOptions.Default.copy(imeAction = ImeAction.Done)
                    },
                    keyboardActions = KeyboardActions(onDone = {
                        if (password == userSettings.customAuthPassword) {
                            handleSuccess()
                        } else {
                            handleFailure()
                        }
                    }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                    enabled = !waiting
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
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                        enabled = !waiting
                    ) {
                        Text("Cancel")
                    }

                    Button(
                        onClick = {
                            if (password == userSettings.customAuthPassword) {
                                handleSuccess()
                            } else {
                                handleFailure()
                            }
                        },
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
