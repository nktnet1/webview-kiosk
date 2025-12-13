package com.nktnet.webview_kiosk.ui.components.setting.fielditems.device

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import android.view.KeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import com.nktnet.webview_kiosk.R
import com.nktnet.webview_kiosk.config.UserSettings
import com.nktnet.webview_kiosk.config.UserSettingsKeys
import com.nktnet.webview_kiosk.managers.ToastManager
import com.nktnet.webview_kiosk.ui.components.setting.fields.CustomSettingFieldItem
import com.nktnet.webview_kiosk.utils.keyEventToShortcutString
import com.nktnet.webview_kiosk.utils.modifierKeyCodes

fun handleUnlockShortcutKeyEvent(
    context: Context,
    event: KeyEvent,
    isListening: Boolean,
    draftValue: String,
): Pair<String, Boolean> {
    if (!isListening) return draftValue to false
    val shortcut = keyEventToShortcutString(event)
    if (shortcut == null) {
        if (event.keyCode !in modifierKeyCodes) {
            ToastManager.show(context, "Shortcut must use CTRL/SHIFT/ALT/META.")
        }
        return draftValue to true
    }
    ToastManager.show(context, "Shortcut: $shortcut")
    return shortcut to false
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun CustomUnlockShortcutSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }
    val coroutineScope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }

    var currentValue by remember { mutableStateOf(userSettings.customUnlockShortcut) }
    var draftValue by remember { mutableStateOf(currentValue) }
    var isListening by remember { mutableStateOf(false) }

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val settingKey = UserSettingsKeys.Device.CUSTOM_UNLOCK_SHORTCUT
    val restricted = userSettings.isRestricted(settingKey)

    LaunchedEffect(isPressed) {
        if (isPressed && !isListening) {
            isListening = true
            coroutineScope.launch { focusRequester.requestFocus() }
        }
    }

    CustomSettingFieldItem(
        label = stringResource(id = R.string.device_custom_unlock_shortcut_title),
        infoText = """
            Provide a custom keyboard shortcut using a modifier key (CTRL/SHIFT/ALT/META)
            in combination with another standard key to unlock/unpin the application.

            For example, CTRL+1.

            This is useful for devices with no navigation buttons on screen and instead
            has a physical keyboard connected.
        """.trimIndent(),
        value = currentValue,
        settingKey = settingKey,
        restricted = restricted,
        onDismissCallback = {
            isListening = false
        },
        onSave = {
            currentValue = draftValue
            userSettings.customUnlockShortcut = draftValue
        },
        bodyContent = {
            Column {
                OutlinedTextField(
                    value = draftValue,
                    onValueChange = {},
                    enabled = !restricted,
                    readOnly = true,
                    singleLine = true,
                    interactionSource = interactionSource,
                    trailingIcon = {
                        if (restricted) {
                            return@OutlinedTextField
                        }
                        IconButton(onClick = {
                            draftValue = ""
                            isListening = false
                        }) {
                            Icon(
                                painter = painterResource(R.drawable.baseline_clear_24),
                                contentDescription = "Clear"
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                        .focusable()
                        .background(
                            if (isListening)
                                androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant
                            else Color.Transparent
                        )
                        .onPreviewKeyEvent { event ->
                            val (newDraft, newListening) =
                                handleUnlockShortcutKeyEvent(
                                    context,
                                    event.nativeKeyEvent,
                                    isListening,
                                    draftValue,
                                )
                            val handled = newDraft != draftValue || newListening != isListening
                            draftValue = newDraft
                            isListening = newListening
                            handled
                        }
                )

                Button(
                    enabled = !restricted,
                    onClick = {
                        if (isListening) {
                            isListening = false
                        } else {
                            isListening = true
                            coroutineScope.launch { focusRequester.requestFocus() }
                        }
                    },
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = if (isListening) {
                            androidx.compose.material3.MaterialTheme.colorScheme.secondary
                        } else {
                            androidx.compose.material3.MaterialTheme.colorScheme.primary
                        }
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                ) {
                    Text(if (isListening) "Listening..." else "Scan Keyboard Shortcut")
                }
            }
        }
    )
}
