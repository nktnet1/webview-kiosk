package uk.nktnet.webviewkiosk.ui.components.setting.fielditems.mqtt.restrictions

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.UserSettingsKeys
import uk.nktnet.webviewkiosk.ui.components.setting.fields.NumberSettingFieldItem

@Composable
fun MqttRestrictionsSendMaximumSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }

    NumberSettingFieldItem(
        label = "Send Maximum",
        infoText = """
            Maximum number of MQTT messages the client can send simultaneously.
        """.trimIndent(),
        placeholder = "e.g. 32",
        initialValue = userSettings.mqttRestrictionsSendMaximum,
        restricted = userSettings.isRestricted(UserSettingsKeys.Mqtt.Restrictions.SEND_MAXIMUM),
        min = 0,
        max = 65535,
        onSave = { userSettings.mqttRestrictionsSendMaximum = it }
    )
}
