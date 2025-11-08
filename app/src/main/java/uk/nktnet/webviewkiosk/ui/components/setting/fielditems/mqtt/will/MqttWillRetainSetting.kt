package uk.nktnet.webviewkiosk.ui.components.setting.fielditems.mqtt.will

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.UserSettingsKeys
import uk.nktnet.webviewkiosk.ui.components.setting.fields.BooleanSettingFieldItem

@Composable
fun MqttWillRetainSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }

    BooleanSettingFieldItem(
        label = "Retain",
        infoText = "Set to true to retain the last will message on the broker after it is sent.",
        restricted = userSettings.isRestricted(UserSettingsKeys.Mqtt.Will.RETAIN),
        initialValue = userSettings.mqttWillRetain,
        onSave = { userSettings.mqttWillRetain = it }
    )
}
