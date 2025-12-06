package com.nktnet.webview_kiosk.ui.components.setting.fielditems.weblifecycle

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.nktnet.webview_kiosk.config.Constants
import com.nktnet.webview_kiosk.config.UserSettings
import com.nktnet.webview_kiosk.config.UserSettingsKeys
import com.nktnet.webview_kiosk.ui.components.setting.fields.NumberSettingFieldItem

@Composable
fun DimScreenOnInactivitySecondsSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }

    NumberSettingFieldItem(
        label = "Dim Screen on Inactivity (seconds)",
        infoText = """
            Number of seconds of inactivity before the screen is dimmed.

            This will set the brightness to 0, and will restore the brightness to
            your device -> brightness setting upon new user interactions.

            Minimum: ${Constants.MIN_INACTIVITY_TIMEOUT_SECONDS}

            To disable, use the value 0.
        """.trimIndent(),
        placeholder = "e.g. 120",
        initialValue = userSettings.dimScreenOnInactivitySeconds,
        restricted = userSettings.isRestricted(UserSettingsKeys.WebLifecycle.DIM_SCREEN_ON_INACTIVITY_SECONDS),
        min = Constants.MIN_INACTIVITY_TIMEOUT_SECONDS,
        onSave = { userSettings.dimScreenOnInactivitySeconds = it }
    )
}
