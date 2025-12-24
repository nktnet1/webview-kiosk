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
fun MqttRestrictionsSendMaximumSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }
    val settingKey = UserSettingsKeys.Mqtt.Restrictions.SEND_MAXIMUM

    NumberSettingFieldItem(
        label = stringResource(R.string.mqtt_restrictions_send_maximum_title),
        infoText = """
            Maximum number of MQTT messages the client can send simultaneously.
        """.trimIndent(),
        placeholder = "e.g. 32",
        initialValue = userSettings.mqttRestrictionsSendMaximum,
        settingKey = settingKey,
        restricted = userSettings.isRestricted(settingKey),
        min = 0,
        max = 65535,
        onSave = { userSettings.mqttRestrictionsSendMaximum = it }
    )
}
