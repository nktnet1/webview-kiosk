package com.nktnet.webview_kiosk.ui.components.setting.fielditems.weblifecycle

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.nktnet.webview_kiosk.config.Constants
import com.nktnet.webview_kiosk.config.UserSettings
import com.nktnet.webview_kiosk.config.UserSettingsKeys
import com.nktnet.webview_kiosk.ui.components.setting.fields.NumberSettingFieldItem

@Composable
fun ResetOnInactivitySecondsSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }

    NumberSettingFieldItem(
        label = "Reset on Inactivity (seconds)",
        infoText = """
            Number of seconds of inactivity before the app resets to the home URL.
            Minimum: ${Constants.MIN_INACTIVITY_TIMEOUT_SECONDS}

            The navigation history will be cleared when resetting.

            To disable, use the value 0. 
        """.trimIndent(),
        placeholder = "e.g. 3600 (for 1 hour)",
        initialValue = userSettings.resetOnInactivitySeconds,
        restricted = userSettings.isRestricted(UserSettingsKeys.WebLifecycle.RESET_ON_INACTIVITY_SECONDS),
        min = Constants.MIN_INACTIVITY_TIMEOUT_SECONDS,
        onSave = { userSettings.resetOnInactivitySeconds = it }
    )
}
