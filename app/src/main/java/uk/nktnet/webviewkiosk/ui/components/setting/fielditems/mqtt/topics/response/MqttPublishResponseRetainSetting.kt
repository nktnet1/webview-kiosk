package uk.nktnet.webviewkiosk.ui.components.setting.fielditems.mqtt.topics.response

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.UserSettingsKeys
import uk.nktnet.webviewkiosk.ui.components.setting.fields.BooleanSettingFieldItem

@Composable
fun MqttPublishResponseRetainSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }

    BooleanSettingFieldItem(
        label = "Retain",
        infoText = """
            Keep response topic messages retained for new subscribers.
        """.trimIndent(),
        initialValue = userSettings.mqttPublishResponseRetain,
        restricted = userSettings.isRestricted(UserSettingsKeys.Mqtt.Topics.Publish.Response.RETAIN),
        onSave = { userSettings.mqttPublishResponseRetain = it }
    )
}
