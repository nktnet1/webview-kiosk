package uk.nktnet.webviewkiosk.ui.components.setting.fielditems.mqtt.will

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import uk.nktnet.webviewkiosk.R
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.UserSettingsKeys
import uk.nktnet.webviewkiosk.ui.components.setting.fields.NumberSettingFieldItem

@Composable
fun MqttWillMessageExpiryIntervalSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }
    val settingKey = UserSettingsKeys.Mqtt.Will.MESSAGE_EXPIRY_INTERVAL

    NumberSettingFieldItem(
        label = stringResource(R.string.mqtt_will_message_expiry_interval_title),
        infoText = """
            The lifetime in seconds of the last will message on the broker
            after it is sent. A value of 0 means the message does not expire.
        """.trimIndent(),
        placeholder = "e.g. 0",
        initialValue = userSettings.mqttWillMessageExpiryInterval,
        settingKey = settingKey,
        restricted = userSettings.isRestricted(settingKey),
        min = 0,
        max = Int.MAX_VALUE,
        onSave = { userSettings.mqttWillMessageExpiryInterval = it }
    )
}
