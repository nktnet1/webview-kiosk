package uk.nktnet.webviewkiosk.ui.components.setting.fielditems.mqtt.connection

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import uk.nktnet.webviewkiosk.R
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.UserSettingsKeys
import uk.nktnet.webviewkiosk.ui.components.setting.fields.BooleanSettingFieldItem

@Composable
fun MqttCleanStartSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }
    val settingKey = UserSettingsKeys.Mqtt.Connection.CLEAN_START

    BooleanSettingFieldItem(
        label = stringResource(R.string.mqtt_connection_clean_start_title),
        infoText = """
            When enabled, the MQTT client will start a new session on connect,
            discarding any previous session state stored by the broker.

            When disabled, the client will resume the previous session
            (subscriptions, in-flight messages, etc.) if it exists.
        """.trimIndent(),
        initialValue = userSettings.mqttCleanStart,
        settingKey = settingKey,
        restricted = userSettings.isRestricted(settingKey),
        onSave = { userSettings.mqttCleanStart = it }
    )
}
