package uk.nktnet.webviewkiosk.ui.components.setting.fielditems.mqtt.topics.request

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.UserSettingsKeys
import uk.nktnet.webviewkiosk.ui.components.setting.fields.BooleanSettingFieldItem

@Composable
fun MqttSubscribeRequestRetainAsPublishedSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }
    val settingKey =
        UserSettingsKeys.Mqtt.Topics.Subscribe.Request.RETAIN_AS_PUBLISHED

    BooleanSettingFieldItem(
        initialValue = userSettings.mqttSubscribeRequestRetainAsPublished,
        settingKey = settingKey,
        restricted = userSettings.isRestricted(settingKey),
        onSave = { userSettings.mqttSubscribeRequestRetainAsPublished = it },
    )
}
