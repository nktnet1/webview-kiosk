package uk.nktnet.webviewkiosk.ui.components.setting.fielditems.mqtt.topics.request

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.UserSettingsKeys
import uk.nktnet.webviewkiosk.ui.components.setting.fields.BooleanSettingFieldItem

@Composable
fun MqttSubscribeRequestRetainedAsPublishedSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }

    BooleanSettingFieldItem(
        label = "Retained As Published",
        infoText = "Keep request topic messages retained for new subscribers.",
        initialValue = userSettings.mqttSubscribeRequestRetainAsPublished,
        restricted = userSettings.isRestricted(UserSettingsKeys.Mqtt.Topics.Subscribe.Request.RETAIN_AS_PUBLISHED),
        onSave = { userSettings.mqttSubscribeRequestRetainAsPublished = it }
    )
}
