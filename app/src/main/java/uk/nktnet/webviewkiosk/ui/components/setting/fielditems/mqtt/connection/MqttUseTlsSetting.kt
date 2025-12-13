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
fun MqttUseTlsSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }

    BooleanSettingFieldItem(
        label = stringResource(R.string.mqtt_connection_use_tls_title),
        infoText = """
            When enabled, the client will connect securely to the broker
            using TLS. Ensure the broker supports TLS on the configured port.
        """.trimIndent(),
        initialValue = userSettings.mqttUseTls,
        restricted = userSettings.isRestricted(UserSettingsKeys.Mqtt.Connection.USE_TLS),
        onSave = { userSettings.mqttUseTls = it }
    )
}
