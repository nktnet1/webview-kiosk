package uk.nktnet.webviewkiosk.ui.components.setting.fielditems.device

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
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
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import uk.nktnet.webviewkiosk.R
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.ui.components.setting.fields.CustomSettingFieldItem
import uk.nktnet.webviewkiosk.utils.keyEventToShortcutString
import uk.nktnet.webviewkiosk.utils.modifierKeyCodes

fun handleUnlockShortcutKeyEvent(
    event: KeyEvent,
    isListening: Boolean,
    draftValue: String,
    showToast: (String) -> Unit
): Pair<String, Boolean> {
    if (!isListening) {
        return draftValue to false
    }
    val shortcut = keyEventToShortcutString(event)
    if (shortcut == null) {
        if (event.nativeKeyEvent.keyCode !in modifierKeyCodes) {
            showToast("Shortcut must use CTRL/SHIFT/ALT/META.")
        }
        return draftValue to true
    }
    showToast("Shortcut: $shortcut")
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
    var toastRef by remember { mutableStateOf<Toast?>(null) }
    val showToast: (String) -> Unit = { msg ->
        toastRef?.cancel()
        toastRef = Toast.makeText(context, msg, Toast.LENGTH_SHORT).apply { show() }
    }

    CustomSettingFieldItem(
        label = "Custom Unlock Shortcut",
        infoText = """
            Provide a custom keyboard shortcut using a modifier key (CTRL/SHIFT/ALT/META)
            in combination with another standard key to unlock/unpin the application.
            
            This is useful for devices without any navigation buttons on screen.
        """.trimIndent(),
        value = currentValue,
        onSave = {
            currentValue = draftValue
            userSettings.customUnlockShortcut = draftValue
        },
        bodyContent = {
            Column {
                OutlinedTextField(
                    value = draftValue,
                    onValueChange = {},
                    readOnly = true,
                    singleLine = true,
                    trailingIcon = {
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
                        .focusRequester(focusRequester)
                        .focusable()
                        .background(if (isListening) androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant else Color.Transparent)
                        .onPreviewKeyEvent { event ->
                            val (newDraft, newListening) = handleUnlockShortcutKeyEvent(event, isListening, draftValue, showToast)
                            val handled = newDraft != draftValue || newListening != isListening
                            draftValue = newDraft
                            isListening = newListening
                            handled
                        }
                )

                Button(
                    onClick = {
                        if (isListening) {
                            isListening = false
                        } else {
                            isListening = true
                            coroutineScope.launch { delay(50); focusRequester.requestFocus() }
                        }
                    },
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = if (isListening) {
                            androidx.compose.material3.MaterialTheme.colorScheme.secondary
                        } else {
                            androidx.compose.material3.MaterialTheme.colorScheme.primary
                        }
                    ),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                ) {
                    Text(if (isListening) "Listening..." else "Scan Shortcut")
                }
            }
        }
    )
}
