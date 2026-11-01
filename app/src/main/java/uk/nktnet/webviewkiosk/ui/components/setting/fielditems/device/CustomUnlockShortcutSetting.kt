package uk.nktnet.webviewkiosk.ui.components.setting.fielditems.device

import android.content.Context
import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import uk.nktnet.webviewkiosk.R
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.UserSettingsKeys
import uk.nktnet.webviewkiosk.managers.ToastManager
import uk.nktnet.webviewkiosk.ui.components.setting.fields.CustomSettingFieldItem
import uk.nktnet.webviewkiosk.utils.keyEventToShortcutString
import uk.nktnet.webviewkiosk.utils.modifierKeyCodes
import uk.nktnet.webviewkiosk.utils.requestFocusWhenPlaced

fun handleUnlockShortcutKeyEvent(
    context: Context,
    event: KeyEvent,
    isListening: Boolean,
    draftValue: String,
): Pair<String, Boolean> {
    if (!isListening) {
        return draftValue to false
    }
    val shortcut = keyEventToShortcutString(event)
    if (shortcut == null) {
        if (event.keyCode !in modifierKeyCodes) {
            ToastManager.show(
                context,
                context.getString(R.string.ui_shortcut_must_use_ctrl_shift_alt_meta),
            )
        }
        return draftValue to true
    }
    ToastManager.show(
        context,
        context.getString(R.string.ui_shortcut_value, shortcut),
    )
    return shortcut to false
}

@Composable
fun CustomUnlockShortcutSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }
    val focusRequester = remember { FocusRequester() }

    var currentValue by remember {
        mutableStateOf(userSettings.customUnlockShortcut)
    }
    var draftValue by remember { mutableStateOf(currentValue) }
    var isListening by remember { mutableStateOf(false) }

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val settingKey = UserSettingsKeys.Device.CUSTOM_UNLOCK_SHORTCUT
    val restricted = userSettings.isRestricted(settingKey)

    LaunchedEffect(isPressed) {
        if (isPressed && !isListening) {
            isListening = true
        }
    }

    CustomSettingFieldItem(
        label = stringResource(R.string.device_custom_unlock_shortcut_title),
        infoText = stringResource(R.string.setting_info_custom_unlock_shortcut),
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
                        IconButton(
                            onClick = {
                                draftValue = ""
                                isListening = false
                            }
                        ) {
                            Icon(
                                painter =
                                    painterResource(R.drawable.baseline_clear_24),
                                contentDescription =
                                    stringResource(R.string.ui_clear),
                            )
                        }
                    },
                    modifier =
                        Modifier.fillMaxWidth()
                            .defaultMinSize(minWidth = 1.dp, minHeight = 1.dp)
                            .requestFocusWhenPlaced(
                                focusRequester = focusRequester,
                                enabled = isListening,
                                requestKey = isListening,
                            )
                            .focusable()
                            .background(
                                if (isListening) {
                                    MaterialTheme.colorScheme.surfaceVariant
                                } else {
                                    Color.Transparent
                                }
                            )
                            .onPreviewKeyEvent { event ->
                                val (newDraft, newListening) =
                                    handleUnlockShortcutKeyEvent(
                                        context,
                                        event.nativeKeyEvent,
                                        isListening,
                                        draftValue,
                                    )
                                val handled =
                                    newDraft != draftValue ||
                                        newListening != isListening
                                draftValue = newDraft
                                isListening = newListening
                                handled
                            },
                )

                Button(
                    enabled = !restricted,
                    onClick = {
                        isListening = !isListening
                    },
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor =
                                if (isListening) {
                                    MaterialTheme.colorScheme.secondary
                                } else {
                                    MaterialTheme.colorScheme.primary
                                }
                        ),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                ) {
                    Text(
                        if (isListening) {
                            stringResource(R.string.ui_listening)
                        } else {
                            stringResource(R.string.ui_scan_keyboard_shortcut)
                        }
                    )
                }
            }
        },
    )
}
