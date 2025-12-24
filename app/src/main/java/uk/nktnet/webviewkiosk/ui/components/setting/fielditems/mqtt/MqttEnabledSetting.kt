package com.nktnet.webview_kiosk.ui.components.setting.fielditems.mqtt

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.nktnet.webview_kiosk.R
import com.nktnet.webview_kiosk.config.Constants
import com.nktnet.webview_kiosk.config.UserSettings
import com.nktnet.webview_kiosk.config.UserSettingsKeys
import com.nktnet.webview_kiosk.managers.MqttManager
import com.nktnet.webview_kiosk.config.mqtt.messages.MqttDisconnectingEvent
import com.nktnet.webview_kiosk.ui.components.setting.fields.BooleanSettingFieldItem
import com.nktnet.webview_kiosk.utils.initMqttForegroundService

@Composable
fun MqttEnabledSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }
    val settingKey = UserSettingsKeys.Mqtt.ENABLED

    BooleanSettingFieldItem(
        label = stringResource(R.string.mqtt_enabled_title),
        infoText = """
            When enabled, ${Constants.APP_NAME} will connect to your configured
            MQTT broker and subscribe/publish to the defined topics.

            This will take effect for new app launches.

            If you are currently in a disconnected state, you will need to manually click
            the "Connect" button after enabling this option (or simply restarts the app).
        """.trimIndent(),
        initialValue = userSettings.mqttEnabled,
        settingKey = settingKey,
        restricted = userSettings.isRestricted(settingKey),
        onSave = { isEnabled ->
            val isChanged = isEnabled != userSettings.mqttEnabled
            if (isChanged) {
                userSettings.mqttEnabled = isEnabled
                if (!isEnabled && MqttManager.isConnected()) {
                    MqttManager.disconnect(
                        cause = MqttDisconnectingEvent.DisconnectCause.USER_INITIATED_SETTINGS_DISABLED
                    )
                    MqttManager.updateConfig(context, false)
                }
                initMqttForegroundService(
                    context,
                    isEnabled && userSettings.mqttUseForegroundService
                )
            }
        }
    )
}
