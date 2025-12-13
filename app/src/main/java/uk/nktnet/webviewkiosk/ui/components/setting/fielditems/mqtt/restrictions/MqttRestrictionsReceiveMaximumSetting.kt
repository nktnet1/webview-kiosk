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
fun MqttRestrictionsReceiveMaximumSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }

    NumberSettingFieldItem(
        label = stringResource(R.string.mqtt_restrictions_receive_maximum_title),
        infoText = """
            Maximum number of MQTT messages the client can receive simultaneously.
        """.trimIndent(),
        placeholder = "e.g. 16",
        initialValue = userSettings.mqttRestrictionsReceiveMaximum,
        restricted = userSettings.isRestricted(UserSettingsKeys.Mqtt.Restrictions.RECEIVE_MAXIMUM),
        min = 0,
        max = 65535,
        onSave = { userSettings.mqttRestrictionsReceiveMaximum = it }
    )
}
