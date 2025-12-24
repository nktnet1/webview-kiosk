package com.nktnet.webview_kiosk.ui.components.setting.fielditems.mqtt.connection

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.nktnet.webview_kiosk.R
import com.nktnet.webview_kiosk.config.UserSettings
import com.nktnet.webview_kiosk.config.UserSettingsKeys
import com.nktnet.webview_kiosk.ui.components.setting.fields.TextSettingFieldItem

@Composable
fun MqttPasswordSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }
    val settingKey = UserSettingsKeys.Mqtt.Connection.PASSWORD

    TextSettingFieldItem(
        label = stringResource(R.string.mqtt_connection_password_title),
        infoText = """
            The password used to authenticate with the MQTT broker.
        """.trimIndent(),
        placeholder = "e.g. **********",
        initialValue = userSettings.mqttPassword,
        settingKey = settingKey,
        restricted = userSettings.isRestricted(settingKey),
        isMultiline = false,
        isPassword = true,
        descriptionFormatter = { v -> if (v.isNotBlank()) "*".repeat(20) else "(blank)" },
        onSave = { userSettings.mqttPassword = it }
    )
}
