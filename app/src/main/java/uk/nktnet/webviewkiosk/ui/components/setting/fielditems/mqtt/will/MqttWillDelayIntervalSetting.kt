package com.nktnet.webview_kiosk.ui.components.setting.fielditems.mqtt.will

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.nktnet.webview_kiosk.R
import com.nktnet.webview_kiosk.config.UserSettings
import com.nktnet.webview_kiosk.config.UserSettingsKeys
import com.nktnet.webview_kiosk.ui.components.setting.fields.NumberSettingFieldItem

@Composable
fun MqttWillDelayIntervalSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }
    val settingKey = UserSettingsKeys.Mqtt.Will.DELAY_INTERVAL

    NumberSettingFieldItem(
        label = stringResource(R.string.mqtt_will_delay_interval_title),
        infoText = """
            Time in seconds the broker will wait before sending the last will message
            after the client disconnects unexpectedly. A value of 0 means immediate delivery.
        """.trimIndent(),
        placeholder = "e.g. 0",
        initialValue = userSettings.mqttWillDelayInterval,
        settingKey = settingKey,
        restricted = userSettings.isRestricted(settingKey),
        min = 0,
        max = Int.MAX_VALUE,
        onSave = { userSettings.mqttWillDelayInterval = it }
    )
}
