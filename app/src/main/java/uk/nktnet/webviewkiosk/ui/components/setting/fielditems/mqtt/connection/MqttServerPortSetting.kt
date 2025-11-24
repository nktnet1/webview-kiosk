package uk.nktnet.webviewkiosk.ui.components.setting.fielditems.mqtt.connection

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.UserSettingsKeys
import uk.nktnet.webviewkiosk.ui.components.setting.fields.NumberSettingFieldItem

@Composable
fun MqttServerPortSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }

    NumberSettingFieldItem(
        label = "Server Port",
        infoText = """
            The TCP port of the MQTT broker the app should connect to.

            Typically,
            - 1883 (TCP Port)
            - 8883 (TLS TCP Port)
        """.trimIndent(),
        initialValue = userSettings.mqttServerPort,
        restricted = userSettings.isRestricted(UserSettingsKeys.Mqtt.Connection.SERVER_PORT),
        min = 0,
        placeholder = "e.g. 1883",
        onSave = { userSettings.mqttServerPort = it }
    )
}
