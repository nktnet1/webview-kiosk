package uk.nktnet.webviewkiosk.ui.components.setting.fielditems.mqtt.topics.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.UserSettingsKeys
import uk.nktnet.webviewkiosk.ui.components.setting.fields.BooleanSettingFieldItem

@Composable
fun MqttSubscribeSettingsRetainAsPublishedSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }

    BooleanSettingFieldItem(
        label = "Retain as Published",
        infoText = """
            Controls whether retained messages from the broker keep their original
            retained flag when delivered to the subscriber.
        """.trimIndent(),
        initialValue = userSettings.mqttSubscribeSettingsRetainAsPublished,
        restricted = userSettings.isRestricted(UserSettingsKeys.Mqtt.Topics.Subscribe.Settings.RETAIN_AS_PUBLISHED),
        onSave = { userSettings.mqttSubscribeSettingsRetainAsPublished = it }
    )
}
