package uk.nktnet.webviewkiosk.ui.components.setting.fielditems.mqtt.connection

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import uk.nktnet.webviewkiosk.R
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.UserSettingsKeys
import uk.nktnet.webviewkiosk.ui.components.setting.fields.NumberSettingFieldItem

@Composable
fun MqttSessionExpiryIntervalSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }
    val settingKey = UserSettingsKeys.Mqtt.Connection.SESSION_EXPIRY_INTERVAL

    NumberSettingFieldItem(
        label = stringResource(R.string.mqtt_connection_session_expiry_interval_title),
        infoText = """
            The session expiry interval in seconds for the MQTT connection.

            Use 0 for immediate expiry on disconnect.

            The max value is ${Int.MAX_VALUE} seconds.
        """.trimIndent(),
        placeholder = "e.g. 60",
        initialValue = userSettings.mqttSessionExpiryInterval,
        settingKey = settingKey,
        restricted = userSettings.isRestricted(settingKey),
        min = 0,
        max = Int.MAX_VALUE,
        descriptionFormatter = { value ->
            if (value == "0") {
                "0 (immediate expiry)"
            } else {
                value
            }
        },
        onSave = { userSettings.mqttSessionExpiryInterval = it }
    )
}
