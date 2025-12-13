package uk.nktnet.webviewkiosk.ui.components.setting.fielditems.mqtt.topics.event

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import uk.nktnet.webviewkiosk.R
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.UserSettingsKeys
import uk.nktnet.webviewkiosk.ui.components.setting.fields.BooleanSettingFieldItem

@Composable
fun MqttPublishEventRetainSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }

    BooleanSettingFieldItem(
        label = stringResource(R.string.mqtt_publish_event_retain_title),
        infoText = """
            Keep event topic messages retained for new subscribers.
        """.trimIndent(),
        initialValue = userSettings.mqttPublishEventRetain,
        restricted = userSettings.isRestricted(UserSettingsKeys.Mqtt.Topics.Publish.Event.RETAIN),
        onSave = { userSettings.mqttPublishEventRetain = it }
    )
}
