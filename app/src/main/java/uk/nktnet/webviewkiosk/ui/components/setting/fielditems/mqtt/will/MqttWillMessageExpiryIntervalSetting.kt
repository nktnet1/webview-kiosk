package uk.nktnet.webviewkiosk.ui.components.setting.fielditems.mqtt.will

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.UserSettingsKeys
import uk.nktnet.webviewkiosk.ui.components.setting.fields.NumberSettingFieldItem

@Composable
fun MqttWillMessageExpiryIntervalSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }

    NumberSettingFieldItem(
        label = "Message Expiry Interval (seconds)",
        infoText = """
            The lifetime in seconds of the last will message on the broker
            after it is sent. A value of 0 means the message does not expire.
        """.trimIndent(),
        placeholder = "e.g. 0",
        initialValue = userSettings.mqttWillMessageExpiryInterval,
        restricted = userSettings.isRestricted(UserSettingsKeys.Mqtt.Will.MESSAGE_EXPIRY_INTERVAL),
        min = 0,
        max = Int.MAX_VALUE,
        onSave = { userSettings.mqttWillMessageExpiryInterval = it }
    )
}
