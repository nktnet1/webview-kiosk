package com.nktnet.webview_kiosk.ui.components.setting.fielditems.device

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.nktnet.webview_kiosk.R
import com.nktnet.webview_kiosk.config.Constants
import com.nktnet.webview_kiosk.config.UserSettings
import com.nktnet.webview_kiosk.config.UserSettingsKeys
import com.nktnet.webview_kiosk.ui.components.setting.fields.TextSettingFieldItem

@Composable
fun CustomAuthPasswordSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }
    val settingKey = UserSettingsKeys.Device.CUSTOM_AUTH_PASSWORD

    val maxCharacters = 128

    TextSettingFieldItem(
        label = stringResource(id = R.string.device_custom_auth_password_title),
        infoText = """
            Specify a custom password to protect your settings or when unlocking from
            the kiosk state.

            For user-owned devices that utilises screen pinning, this will only work
            if you are using an unlock method provided by the app.

            Device-level unpin methods (e.g. gestures/holding overview + back button
            simultaneously) will bypass this setting. To enhance security, please
            see: ${Constants.WEBSITE_URL}/docs/security

            Leave this setting blank to use your device's biometrics or credentials.
        """.trimIndent(),
        placeholder = "(blank = device credentials)",
        initialValue = userSettings.customAuthPassword,
        settingKey = settingKey,
        restricted = userSettings.isRestricted(settingKey),
        isMultiline = false,
        isPassword = true,
        validator = { it.length <= maxCharacters },
        validationMessage = "Please enter fewer than $maxCharacters characters.",
        descriptionFormatter = { v -> if (v.isNotBlank()) "*".repeat(20) else "(blank)" },
        onSave = { userSettings.customAuthPassword = it }
    )
}
