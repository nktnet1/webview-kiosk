package uk.nktnet.webviewkiosk.ui.components.setting.fielditems.mqtt.connection

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import uk.nktnet.webviewkiosk.R
import uk.nktnet.webviewkiosk.config.Constants
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.UserSettingsKeys
import uk.nktnet.webviewkiosk.ui.components.setting.fields.BooleanSettingFieldItem
import uk.nktnet.webviewkiosk.ui.screens.settingDefinition

@Composable
fun MqttAutomaticReconnectSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }
    val settingKey = UserSettingsKeys.Mqtt.Connection.AUTOMATIC_RECONNECT

    BooleanSettingFieldItem(
        infoText =
            stringResource(
                settingDefinition(settingKey).descriptionRes,
                stringResource(R.string.app_name),
                Constants.MQTT_AUTO_RECONNECT_INTERVAL_SECONDS,
            ),
        initialValue = userSettings.mqttAutomaticReconnect,
        settingKey = settingKey,
        restricted = userSettings.isRestricted(settingKey),
        onSave = { userSettings.mqttAutomaticReconnect = it },
    )
}
