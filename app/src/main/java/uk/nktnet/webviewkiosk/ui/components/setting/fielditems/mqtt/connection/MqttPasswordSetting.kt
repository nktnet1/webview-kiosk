package uk.nktnet.webviewkiosk.ui.components.setting.fielditems.mqtt.connection

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.UserSettingsKeys
import uk.nktnet.webviewkiosk.ui.components.setting.fields.TextSettingFieldItem

@Composable
fun MqttPasswordSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }

    val restricted = userSettings.isRestricted(UserSettingsKeys.Mqtt.Connection.PASSWORD)

    TextSettingFieldItem(
        label = "Password",
        infoText = """
            The password used to authenticate with the MQTT broker.
        """.trimIndent(),
        placeholder = "e.g. **********",
        initialValue = userSettings.mqttPassword,
        restricted = restricted,
        isMultiline = false,
        isPassword = true,
        descriptionFormatter = { v -> if (v.isNotBlank()) "*".repeat(20) else "(blank)" },
        onSave = { userSettings.mqttPassword = it }
    )
}
