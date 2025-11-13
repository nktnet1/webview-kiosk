package uk.nktnet.webviewkiosk.ui.components.setting.fielditems.device

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import uk.nktnet.webviewkiosk.config.Constants
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.UserSettingsKeys
import uk.nktnet.webviewkiosk.ui.components.setting.fields.TextSettingFieldItem

@Composable
fun CustomAuthPasswordSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }
    val restricted = userSettings.isRestricted(UserSettingsKeys.Device.CUSTOM_AUTH_PASSWORD)

    TextSettingFieldItem(
        label = "Custom Auth Password",
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
        restricted = restricted,
        isMultiline = false,
        isPassword = true,
        descriptionFormatter = { v -> if (v.isNotBlank()) "*".repeat(20) else "(blank)" },
        onSave = { userSettings.customAuthPassword = it }
    )
}
