package com.nktnet.webview_kiosk.ui.components.setting.fielditems.mqtt.topics.response

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.nktnet.webview_kiosk.R
import com.nktnet.webview_kiosk.config.UserSettings
import com.nktnet.webview_kiosk.config.UserSettingsKeys
import com.nktnet.webview_kiosk.ui.components.setting.fields.BooleanSettingFieldItem

@Composable
fun MqttPublishResponseRetainSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }
    val settingKey = UserSettingsKeys.Mqtt.Topics.Publish.Response.RETAIN

    BooleanSettingFieldItem(
        label = stringResource(R.string.mqtt_publish_response_retain_title),
        infoText = """
            Keep response topic messages retained for new subscribers.
        """.trimIndent(),
        initialValue = userSettings.mqttPublishResponseRetain,
        settingKey = settingKey,
        restricted = userSettings.isRestricted(settingKey),
        onSave = { userSettings.mqttPublishResponseRetain = it }
    )
}
