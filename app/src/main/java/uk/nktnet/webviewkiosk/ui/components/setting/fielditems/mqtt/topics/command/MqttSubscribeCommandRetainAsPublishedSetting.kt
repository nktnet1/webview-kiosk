package uk.nktnet.webviewkiosk.ui.components.setting.fielditems.mqtt.topics.command

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import uk.nktnet.webviewkiosk.R
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.UserSettingsKeys
import uk.nktnet.webviewkiosk.ui.components.setting.fields.BooleanSettingFieldItem

@Composable
fun MqttSubscribeCommandRetainAsPublishedSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }
    val settingKey = UserSettingsKeys.Mqtt.Topics.Subscribe.Command.RETAIN_AS_PUBLISHED

    BooleanSettingFieldItem(
        label = stringResource(R.string.mqtt_subscribe_command_retain_as_published_title),
        infoText = """
            Controls whether retained messages from the broker keep their original
            retained flag when delivered to the subscriber.
        """.trimIndent(),
        initialValue = userSettings.mqttSubscribeCommandRetainAsPublished,
        settingKey = settingKey,
        restricted = userSettings.isRestricted(settingKey),
        onSave = { userSettings.mqttSubscribeCommandRetainAsPublished = it }
    )
}
