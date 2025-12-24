package com.nktnet.webview_kiosk.ui.components.setting.fielditems.mqtt.topics.request

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.nktnet.webview_kiosk.R
import com.nktnet.webview_kiosk.config.UserSettings
import com.nktnet.webview_kiosk.config.UserSettingsKeys
import com.nktnet.webview_kiosk.ui.components.setting.fields.BooleanSettingFieldItem

@Composable
fun MqttSubscribeRequestRetainAsPublishedSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }
    val settingKey = UserSettingsKeys.Mqtt.Topics.Subscribe.Request.RETAIN_AS_PUBLISHED

    BooleanSettingFieldItem(
        label = stringResource(R.string.mqtt_subscribe_request_retain_as_published_title),
        infoText = """
            Controls whether retained messages from the broker keep their original
            retained flag when delivered to the subscriber.
        """.trimIndent(),
        initialValue = userSettings.mqttSubscribeRequestRetainAsPublished,
        settingKey = settingKey,
        restricted = userSettings.isRestricted(settingKey),
        onSave = { userSettings.mqttSubscribeRequestRetainAsPublished = it }
    )
}
