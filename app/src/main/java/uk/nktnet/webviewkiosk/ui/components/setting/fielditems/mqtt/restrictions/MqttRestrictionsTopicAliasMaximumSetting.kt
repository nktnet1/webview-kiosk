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
fun MqttRestrictionsTopicAliasMaximumSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }
    val settingKey = UserSettingsKeys.Mqtt.Restrictions.TOPIC_ALIAS_MAXIMUM

    NumberSettingFieldItem(
        label = stringResource(R.string.mqtt_restrictions_send_topic_alias_maximum_title),
        infoText = """
            Maximum number of topic aliases the client can receive.
        """.trimIndent(),
        placeholder = "e.g. 0",
        initialValue = userSettings.mqttRestrictionsTopicAliasMaximum,
        settingKey = settingKey,
        restricted = userSettings.isRestricted(settingKey),
        min = 0,
        max = 65_535,
        onSave = { userSettings.mqttRestrictionsTopicAliasMaximum = it }
    )
}
