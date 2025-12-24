package com.nktnet.webview_kiosk.ui.components.setting.fielditems.mqtt.topics.request

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.nktnet.webview_kiosk.R
import com.nktnet.webview_kiosk.config.Constants
import com.nktnet.webview_kiosk.config.UserSettings
import com.nktnet.webview_kiosk.config.UserSettingsKeys
import com.nktnet.webview_kiosk.config.mqtt.MqttRetainHandlingOption
import com.nktnet.webview_kiosk.ui.components.setting.fields.DropdownSettingFieldItem

@Composable
fun MqttSubscribeRequestRetainHandlingSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }
    val settingKey = UserSettingsKeys.Mqtt.Topics.Subscribe.Request.RETAIN_HANDLING

    DropdownSettingFieldItem(
        label = stringResource(R.string.mqtt_subscribe_request_retain_handling_title),
        infoText = """
            Control whether ${Constants.APP_NAME}} should receive existing
            retained messages when subscribing.
        """.trimIndent(),
        options = MqttRetainHandlingOption.entries,
        initialValue = userSettings.mqttSubscribeRequestRetainHandling,
        settingKey = settingKey,
        restricted = userSettings.isRestricted(settingKey),
        onSave = { userSettings.mqttSubscribeRequestRetainHandling = it },
        itemText = { it.getSettingLabel() },
    )
}
