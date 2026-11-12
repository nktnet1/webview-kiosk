package uk.nktnet.webviewkiosk.ui.components.setting.fielditems.mqtt

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import uk.nktnet.webviewkiosk.R
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.UserSettingsKeys
import uk.nktnet.webviewkiosk.config.remote.outbound.OutboundDisconnectingEvent
import uk.nktnet.webviewkiosk.managers.MqttManager
import uk.nktnet.webviewkiosk.ui.components.setting.fields.BooleanSettingFieldItem
import uk.nktnet.webviewkiosk.ui.screens.settingDefinition
import uk.nktnet.webviewkiosk.utils.initMqttForegroundService

@Composable
fun MqttEnabledSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }
    val settingKey = UserSettingsKeys.Mqtt.ENABLED

    BooleanSettingFieldItem(
        infoText =
            stringResource(
                settingDefinition(settingKey).descriptionRes,
                stringResource(R.string.app_name),
            ),
        initialValue = userSettings.mqttEnabled,
        settingKey = settingKey,
        restricted = userSettings.isRestricted(settingKey),
        onSave = { isEnabled ->
            val isChanged = isEnabled != userSettings.mqttEnabled
            if (isChanged) {
                userSettings.mqttEnabled = isEnabled
                MqttManager.updateConfig(context.applicationContext, false)
                if (!isEnabled) {
                    MqttManager.disconnect(
                        cause =
                            OutboundDisconnectingEvent.DisconnectCause
                                .USER_INITIATED_SETTINGS_DISABLED
                    )
                }
                initMqttForegroundService(
                    context,
                    isEnabled && userSettings.mqttUseForegroundService,
                )
            }
        },
    )
}
