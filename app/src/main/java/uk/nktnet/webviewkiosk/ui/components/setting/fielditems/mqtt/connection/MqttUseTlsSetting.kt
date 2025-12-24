package com.nktnet.webview_kiosk.ui.components.setting.fielditems.mqtt.connection

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.nktnet.webview_kiosk.R
import com.nktnet.webview_kiosk.config.UserSettings
import com.nktnet.webview_kiosk.config.UserSettingsKeys
import com.nktnet.webview_kiosk.ui.components.setting.fields.BooleanSettingFieldItem

@Composable
fun MqttUseTlsSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }
    val settingKey = UserSettingsKeys.Mqtt.Connection.USE_TLS

    BooleanSettingFieldItem(
        label = stringResource(R.string.mqtt_connection_use_tls_title),
        infoText = """
            When enabled, the client will connect securely to the broker
            using TLS. Ensure the broker supports TLS on the configured port.
        """.trimIndent(),
        initialValue = userSettings.mqttUseTls,
        settingKey = settingKey,
        restricted = userSettings.isRestricted(settingKey),
        onSave = { userSettings.mqttUseTls = it }
    )
}
