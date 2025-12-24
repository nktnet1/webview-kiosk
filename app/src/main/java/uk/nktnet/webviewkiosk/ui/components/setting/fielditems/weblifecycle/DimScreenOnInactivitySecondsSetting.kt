package com.nktnet.webview_kiosk.ui.components.setting.fielditems.weblifecycle

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.nktnet.webview_kiosk.R
import com.nktnet.webview_kiosk.config.Constants
import com.nktnet.webview_kiosk.config.UserSettings
import com.nktnet.webview_kiosk.config.UserSettingsKeys
import com.nktnet.webview_kiosk.ui.components.setting.fields.NumberSettingFieldItem

@Composable
fun DimScreenOnInactivitySecondsSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }
    val settingKey = UserSettingsKeys.WebLifecycle.DIM_SCREEN_ON_INACTIVITY_SECONDS

    NumberSettingFieldItem(
        label = stringResource(id = R.string.web_lifecycle_dim_screen_on_inactivity_seconds_title),
        infoText = """
            Number of seconds of inactivity before the screen is dimmed.

            This will set the brightness to 0, and will restore the brightness to
            your device -> brightness setting upon new user interactions.

            Minimum: ${Constants.MIN_INACTIVITY_TIMEOUT_SECONDS}

            To disable, use the value 0.
        """.trimIndent(),
        placeholder = "e.g. 120",
        initialValue = userSettings.dimScreenOnInactivitySeconds,
        settingKey = settingKey,
        restricted = userSettings.isRestricted(settingKey),
        min = Constants.MIN_INACTIVITY_TIMEOUT_SECONDS,
        onSave = { userSettings.dimScreenOnInactivitySeconds = it }
    )
}
