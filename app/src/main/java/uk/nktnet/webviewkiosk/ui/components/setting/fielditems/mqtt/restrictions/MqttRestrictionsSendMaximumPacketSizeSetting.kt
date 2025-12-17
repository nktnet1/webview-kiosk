package uk.nktnet.webviewkiosk.ui.components.setting.fielditems.mqtt.restrictions

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import uk.nktnet.webviewkiosk.R
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.UserSettingsKeys
import uk.nktnet.webviewkiosk.ui.components.setting.fields.NumberSettingFieldItem

@Composable
fun MqttRestrictionsSendMaximumPacketSizeSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }
    val settingKey = UserSettingsKeys.Mqtt.Restrictions.SEND_MAXIMUM_PACKET_SIZE

    NumberSettingFieldItem(
        label = stringResource(R.string.mqtt_restrictions_send_maximum_packet_size_title),
        infoText = """
            Maximum size in bytes of MQTT packets the client can send.
        """.trimIndent(),
        placeholder = "e.g. 1024",
        initialValue = userSettings.mqttRestrictionsSendMaximumPacketSize,
        settingKey = settingKey,
        restricted = userSettings.isRestricted(settingKey),
        min = 0,
        max = 268_435_460,
        onSave = { userSettings.mqttRestrictionsSendMaximumPacketSize = it }
    )
}
