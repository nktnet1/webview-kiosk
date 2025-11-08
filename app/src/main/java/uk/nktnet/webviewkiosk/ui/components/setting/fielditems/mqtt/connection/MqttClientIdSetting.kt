package uk.nktnet.webviewkiosk.ui.components.setting.fielditems.mqtt.connection

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import uk.nktnet.webviewkiosk.config.SystemSettings
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.UserSettingsKeys
import uk.nktnet.webviewkiosk.mqtt.MqttManager
import uk.nktnet.webviewkiosk.ui.components.setting.fields.TextSettingFieldItem

@Composable
fun MqttClientIdSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }
    val systemSettings = remember { SystemSettings(context) }

    val restricted = userSettings.isRestricted(UserSettingsKeys.Mqtt.Connection.CLIENT_ID)

    TextSettingFieldItem(
        label = "Client ID",
        infoText = """
            A unique identifier for this client when connecting to the MQTT broker.
            Supports the variable APP_INSTANCE_ID, which you can use like:
            - wk-${'$'}{APP_INSTANCE_ID}
        """.trimIndent(),
        placeholder = "e.g. wk-${'$'}{APP_INSTANCE_ID}",
        initialValue = userSettings.mqttClientId,
        descriptionFormatter = {
            MqttManager.mqttVariableReplacement(systemSettings, it)
        },
        restricted = restricted,
        isMultiline = false,
        onSave = { userSettings.mqttClientId = it }
    )
}
