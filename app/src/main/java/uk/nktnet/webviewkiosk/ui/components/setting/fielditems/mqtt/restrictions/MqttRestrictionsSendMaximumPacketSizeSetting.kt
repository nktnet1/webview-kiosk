package uk.nktnet.webviewkiosk.ui.components.setting.fielditems.mqtt.restrictions

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.UserSettingsKeys
import uk.nktnet.webviewkiosk.ui.components.setting.fields.NumberSettingFieldItem

@Composable
fun MqttRestrictionsSendMaximumPacketSizeSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }

    NumberSettingFieldItem(
        label = "Send Maximum Packet Size",
        infoText = """
            Maximum size in bytes of MQTT packets the client can send.
        """.trimIndent(),
        placeholder = "e.g. 1024",
        initialValue = userSettings.mqttRestrictionsSendMaximumPacketSize,
        restricted = userSettings.isRestricted(UserSettingsKeys.Mqtt.Restrictions.SEND_MAXIMUM_PACKET_SIZE),
        min = 0,
        max = 268_435_460,
        onSave = { userSettings.mqttRestrictionsSendMaximumPacketSize = it }
    )
}
