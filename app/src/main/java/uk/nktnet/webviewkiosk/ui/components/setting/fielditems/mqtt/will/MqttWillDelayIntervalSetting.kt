package uk.nktnet.webviewkiosk.ui.components.setting.fielditems.mqtt.will

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.UserSettingsKeys
import uk.nktnet.webviewkiosk.ui.components.setting.fields.NumberSettingFieldItem

@Composable
fun MqttWillDelayIntervalSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }

    NumberSettingFieldItem(
        label = "Will Delay Interval (seconds)",
        infoText = """
            Time in seconds the broker will wait before sending the last will message
            after the client disconnects unexpectedly. A value of 0 means immediate delivery.
        """.trimIndent(),
        placeholder = "e.g. 0",
        initialValue = userSettings.mqttWillDelayInterval,
        restricted = userSettings.isRestricted(UserSettingsKeys.Mqtt.Will.DELAY_INTERVAL),
        min = 0,
        max = Int.MAX_VALUE,
        onSave = { userSettings.mqttWillDelayInterval = it }
    )
}
