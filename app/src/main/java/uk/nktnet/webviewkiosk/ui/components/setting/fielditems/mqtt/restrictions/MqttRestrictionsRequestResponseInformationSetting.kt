package com.nktnet.webview_kiosk.ui.components.setting.fielditems.mqtt.restrictions

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.nktnet.webview_kiosk.R
import com.nktnet.webview_kiosk.config.UserSettings
import com.nktnet.webview_kiosk.config.UserSettingsKeys
import com.nktnet.webview_kiosk.ui.components.setting.fields.BooleanSettingFieldItem

@Composable
fun MqttRestrictionsRequestResponseInformationSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }
    val settingKey = UserSettingsKeys.Mqtt.Restrictions.REQUEST_RESPONSE_INFORMATION

    BooleanSettingFieldItem(
        label = stringResource(R.string.mqtt_restrictions_request_response_information_title),
        infoText = """
            When enabled, the client requests additional response information
            from the broker in MQTT responses.
        """.trimIndent(),
        initialValue = userSettings.mqttRestrictionsRequestResponseInformation,
        settingKey = settingKey,
        restricted = userSettings.isRestricted(settingKey),
        onSave = { userSettings.mqttRestrictionsRequestResponseInformation = it }
    )
}
