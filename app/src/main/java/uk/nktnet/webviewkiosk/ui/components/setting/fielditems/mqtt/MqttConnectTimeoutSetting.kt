package uk.nktnet.webviewkiosk.ui.components.setting.fielditems.mqtt

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.UserSettingsKeys
import uk.nktnet.webviewkiosk.ui.components.setting.fields.NumberSettingFieldItem

@Composable
fun MqttConnectTimeoutSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }

    NumberSettingFieldItem(
        label = "Connect Timeout (seconds)",
        infoText = """
            Maximum time in seconds the MQTT client will wait when attempting
            to connect to the broker before timing out.
        """.trimIndent(),
        placeholder = "e.g. 30",
        initialValue = userSettings.mqttConnectTimeout,
        restricted = userSettings.isRestricted(UserSettingsKeys.Mqtt.CONNECT_TIMEOUT),
        min = 0,
        max = 300,
        onSave = { userSettings.mqttConnectTimeout = it }
    )
}
