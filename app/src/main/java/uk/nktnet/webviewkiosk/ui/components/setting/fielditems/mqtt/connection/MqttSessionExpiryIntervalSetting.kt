package uk.nktnet.webviewkiosk.ui.components.setting.fielditems.mqtt.connection

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import uk.nktnet.webviewkiosk.R
import uk.nktnet.webviewkiosk.config.Constants
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
            Specify the maximum time a session is retained on the server after
            disconnecting from the network.

            The server will discard the corresponding session state when the
            expiration time is reached.
        """.trimIndent(),
        placeholder = "e.g. 60",
        initialValue = userSettings.mqttSessionExpiryInterval,
        settingKey = settingKey,
        restricted = userSettings.isRestricted(settingKey),
        min = 0,
        max = Constants.MAX_INT_SETTING,
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
