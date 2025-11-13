package uk.nktnet.webviewkiosk.ui.components.setting.fielditems.device

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
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
            Specify a custom password to protect your settings or unlock the device.
            Leave empty to use your device's password.
        """.trimIndent(),
        placeholder = "(optional)",
        initialValue = userSettings.customAuthPassword,
        restricted = restricted,
        isMultiline = false,
        isPassword = true,
        descriptionFormatter = { v -> if (v.isNotBlank()) "*".repeat(20) else "(blank)" },
        onSave = { userSettings.customAuthPassword = it }
    )
}
