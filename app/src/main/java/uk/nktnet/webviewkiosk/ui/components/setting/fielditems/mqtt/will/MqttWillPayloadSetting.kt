package uk.nktnet.webviewkiosk.ui.components.setting.fielditems.mqtt.will

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.UserSettingsKeys
import uk.nktnet.webviewkiosk.ui.components.setting.fields.TextSettingFieldItem

@Composable
fun MqttWillPayloadSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }

    val restricted = userSettings.isRestricted(UserSettingsKeys.Mqtt.Will.PAYLOAD)

    TextSettingFieldItem(
        label = "Payload",
        infoText = "The MQTT payload to send for the last will message if the client disconnects unexpectedly.",
        placeholder = "e.g. Client disconnected",
        initialValue = userSettings.mqttWillPayload,
        restricted = restricted,
        isMultiline = true,
        onSave = { userSettings.mqttWillPayload = it },
    )
}
