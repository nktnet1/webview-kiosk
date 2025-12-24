package com.nktnet.webview_kiosk.ui.components.setting.fielditems.mqtt.restrictions

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.nktnet.webview_kiosk.R
import com.nktnet.webview_kiosk.config.UserSettings
import com.nktnet.webview_kiosk.config.UserSettingsKeys
import com.nktnet.webview_kiosk.ui.components.setting.fields.NumberSettingFieldItem

@Composable
fun MqttRestrictionsReceiveMaximumSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }
    val settingKey = UserSettingsKeys.Mqtt.Restrictions.RECEIVE_MAXIMUM

    NumberSettingFieldItem(
        label = stringResource(R.string.mqtt_restrictions_receive_maximum_title),
        infoText = """
            Maximum number of MQTT messages the client can receive simultaneously.
        """.trimIndent(),
        placeholder = "e.g. 16",
        initialValue = userSettings.mqttRestrictionsReceiveMaximum,
        settingKey = settingKey,
        restricted = userSettings.isRestricted(settingKey),
        min = 0,
        max = 65535,
        onSave = { userSettings.mqttRestrictionsReceiveMaximum = it }
    )
}
