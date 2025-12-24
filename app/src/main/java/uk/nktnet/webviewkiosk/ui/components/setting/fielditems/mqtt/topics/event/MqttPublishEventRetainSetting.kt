package com.nktnet.webview_kiosk.ui.components.setting.fielditems.mqtt.topics.event

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.nktnet.webview_kiosk.R
import com.nktnet.webview_kiosk.config.UserSettings
import com.nktnet.webview_kiosk.config.UserSettingsKeys
import com.nktnet.webview_kiosk.ui.components.setting.fields.BooleanSettingFieldItem

@Composable
fun MqttPublishEventRetainSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }
    val settingKey = UserSettingsKeys.Mqtt.Topics.Publish.Event.RETAIN

    BooleanSettingFieldItem(
        label = stringResource(R.string.mqtt_publish_event_retain_title),
        infoText = """
            Keep event topic messages retained for new subscribers.
        """.trimIndent(),
        initialValue = userSettings.mqttPublishEventRetain,
        settingKey = settingKey,
        restricted = userSettings.isRestricted(settingKey),
        onSave = { userSettings.mqttPublishEventRetain = it }
    )
}
