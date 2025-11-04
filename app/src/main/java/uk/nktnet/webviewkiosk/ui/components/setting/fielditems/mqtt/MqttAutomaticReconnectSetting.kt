package uk.nktnet.webviewkiosk.ui.components.setting.fielditems.mqtt

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.UserSettingsKeys
import uk.nktnet.webviewkiosk.ui.components.setting.fields.BooleanSettingFieldItem

@Composable
fun MqttAutomaticReconnectSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }

    BooleanSettingFieldItem(
        label = "Automatic Reconnect",
        infoText = """
            Automatically reconnect to the MQTT broker after an unexpected disconnect.
            Uses exponential backoff delays when enabled.
        """.trimIndent(),
        initialValue = userSettings.mqttAutomaticReconnect,
        restricted = userSettings.isRestricted(UserSettingsKeys.Mqtt.AUTOMATIC_RECONNECT),
        onSave = { userSettings.mqttAutomaticReconnect = it }
    )
}
