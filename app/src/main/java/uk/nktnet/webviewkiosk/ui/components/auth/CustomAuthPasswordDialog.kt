package uk.nktnet.webviewkiosk.ui.components.auth

import android.os.Build
import android.view.View
import android.view.ViewTreeObserver
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.view.SoftwareKeyboardControllerCompat
import androidx.core.view.ViewCompat
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import uk.nktnet.webviewkiosk.R
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.managers.AuthenticationManager
import uk.nktnet.webviewkiosk.managers.ToastManager
import uk.nktnet.webviewkiosk.ui.components.common.SystemSafeDialog as Dialog
import uk.nktnet.webviewkiosk.utils.requestFocusWhenPlaced
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun CustomAuthPasswordDialog() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }

    val state by AuthenticationManager.promptResults.collectAsState()

    if (
        !AuthenticationManager.showCustomAuth.value
        || state != AuthenticationManager.AuthenticationResult.Pending
    ) {
        return
    }

    val focusRequester = remember { FocusRequester() }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var waiting by remember { mutableStateOf(false) }
    var isError by remember { mutableStateOf(false) }
    var dismissRequested by remember { mutableStateOf(false) }
    val parentView = LocalView.current

    Dialog(
        onDismissRequest = { dismissRequested = true }
    ) {
        val focusManager = LocalFocusManager.current
        val dialogView = LocalView.current
        val needsLegacyImeCleanup = Build.VERSION.SDK_INT < Build.VERSION_CODES.R

        val dismissKeyboard = remember(dialogView, parentView, focusManager) {
            var dismissed = false
            val dismiss: () -> Unit = {
                if (needsLegacyImeCleanup && !dismissed) {
                    dismissed = true
                    // Send the hide request while the dialog still has a window token.
                    // Compose's keyboard controller queues it until a later frame.
                    SoftwareKeyboardControllerCompat(dialogView).hide()
                    focusManager.clearFocus(force = true)
                    restoreLegacyImeInsets(parentView)
                }
            }
            dismiss
        }

        if (needsLegacyImeCleanup) {
            DisposableEffect(dismissKeyboard) {
                onDispose {
                    dismissKeyboard()
                }
            }
        }

        LaunchedEffect(dismissRequested) {
            if (dismissRequested) {
                dismissKeyboard()
                AuthenticationManager.customAuthCancel()
            }
        }

        fun handleUnlock() {
            scope.launch {
                if (waiting || dismissRequested) {
                    return@launch
                }
                waiting = true
                val start = System.currentTimeMillis()
                if (password == userSettings.customAuthPassword) {
                    password = ""
                    dismissKeyboard()
                    AuthenticationManager.customAuthSuccess()
                } else {
                    val elapsed = System.currentTimeMillis() - start
                    val remaining = 1000L - elapsed
                    if (remaining > 0) {
                        delay(remaining.milliseconds)
                    }
                    isError = true
                    ToastManager.show(context, "Incorrect password")
                }
                waiting = false
            }
        }

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
                    visualTransformation = if (showPassword) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
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
                        .defaultMinSize(minWidth = 1.dp, minHeight = 1.dp)
                        .requestFocusWhenPlaced(focusRequester),
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

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(top = 2.dp, start = 2.dp),
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

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextButton(
                        onClick = {
                            password = ""
                            dismissRequested = true
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

private fun restoreLegacyImeInsets(view: View) {
    // Window focus returns asynchronously after the dialog is removed. Refresh the activity's
    // insets then, so its Compose content does not retain the dialog keyboard's bottom padding.
    view.post {
        if (!view.isAttachedToWindow) {
            return@post
        }

        fun restoreInsets() {
            SoftwareKeyboardControllerCompat(view).hide()
            ViewCompat.requestApplyInsets(view)
        }

        if (view.hasWindowFocus()) {
            restoreInsets()
        } else {
            val observer = view.viewTreeObserver
            val listener = object : ViewTreeObserver.OnWindowFocusChangeListener,
                View.OnAttachStateChangeListener {
                private fun removeListeners() {
                    if (observer.isAlive) {
                        observer.removeOnWindowFocusChangeListener(this)
                    }
                    view.removeOnAttachStateChangeListener(this)
                }

                override fun onWindowFocusChanged(hasFocus: Boolean) {
                    if (hasFocus) {
                        removeListeners()
                        restoreInsets()
                    }
                }

                override fun onViewAttachedToWindow(v: View) {}

                override fun onViewDetachedFromWindow(v: View) {
                    removeListeners()
                }
            }
            observer.addOnWindowFocusChangeListener(listener)
            view.addOnAttachStateChangeListener(listener)
        }
    }
}
