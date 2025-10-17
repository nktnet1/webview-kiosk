package com.nktnet.webview_kiosk.ui.components.setting

import android.webkit.HttpAuthHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.nktnet.webview_kiosk.R

@Composable
fun BasicAuthDialog(authHandler: HttpAuthHandler?, host: String?, realm: String?, onDismiss: () -> Unit) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }

    if (authHandler != null) {
        AlertDialog(
            onDismissRequest = {
                authHandler.cancel()
                onDismiss()
                username = ""
                password = ""
            },
            title = { Text("Authentication Required") },
            text = {
                Column {
                    Text("Host: ${host ?: ""}")
                    Text("Realm: ${realm ?: ""}")
                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        label = { Text("Username") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Next),
                        keyboardActions = KeyboardActions(),
                        modifier = Modifier.padding(top = 12.dp)
                    )
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Password") },
                        visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                        singleLine = true,
                        trailingIcon = {
                            IconButton(onClick = { showPassword = !showPassword }) {
                                Icon(
                                    painter = painterResource(
                                        if (showPassword) R.drawable.outline_visibility_24
                                        else R.drawable.outline_visibility_off_24
                                    ),
                                    contentDescription = if (showPassword) "Hide password" else "Show password"
                                )
                            }
                        },
                        keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = {
                            authHandler.proceed(username, password)
                            onDismiss()
                        }),
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        authHandler.proceed(username, password)
                        onDismiss()
                        username = ""
                        password = ""
                    },
                    modifier = Modifier.width(95.dp)
                ) { Text("Login") }
            },
            dismissButton = {
                Button(
                    onClick = {
                        authHandler.cancel()
                        onDismiss()
                        username = ""
                        password = ""
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    modifier = Modifier.width(95.dp)
                ) { Text("Cancel") }
            }

        )
    }
}
