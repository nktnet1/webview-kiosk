package uk.nktnet.webviewkiosk.ui.components.setting.fielditems.mqtt

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.UserSettingsKeys
import uk.nktnet.webviewkiosk.ui.components.setting.fields.TextSettingFieldItem

@Composable
fun MqttUsernameSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }

    val restricted = userSettings.isRestricted(UserSettingsKeys.Mqtt.USERNAME)

    TextSettingFieldItem(
        label = "MQTT Username",
        infoText = """
            The username used to authenticate with the MQTT broker.
        """.trimIndent(),
        placeholder = "e.g. user001",
        initialValue = userSettings.mqttUsername,
        restricted = restricted,
        isMultiline = false,
        onSave = { userSettings.mqttUsername = it }
    )
}
