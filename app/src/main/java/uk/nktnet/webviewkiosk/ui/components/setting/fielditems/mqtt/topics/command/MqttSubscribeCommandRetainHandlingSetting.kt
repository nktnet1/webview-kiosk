package com.nktnet.webview_kiosk.ui.components.setting.fielditems.mqtt.topics.command

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
fun MqttSubscribeCommandRetainHandlingSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }
    val settingKey = UserSettingsKeys.Mqtt.Topics.Subscribe.Command.RETAIN_HANDLING

    DropdownSettingFieldItem(
        label = stringResource(R.string.mqtt_subscribe_command_retain_handling_title),
        infoText = """
            Control whether ${Constants.APP_NAME}} should receive existing
            retained messages when subscribing.
        """.trimIndent(),
        options = MqttRetainHandlingOption.entries,
        initialValue = userSettings.mqttSubscribeCommandRetainHandling,
        settingKey = settingKey,
        restricted = userSettings.isRestricted(settingKey),
        onSave = { userSettings.mqttSubscribeCommandRetainHandling = it },
        itemText = { it.getSettingLabel() },
    )
}
