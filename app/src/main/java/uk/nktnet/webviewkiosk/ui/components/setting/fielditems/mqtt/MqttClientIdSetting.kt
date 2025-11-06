package uk.nktnet.webviewkiosk.ui.components.setting.fielditems.mqtt

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.UserSettingsKeys
import uk.nktnet.webviewkiosk.ui.components.setting.fields.TextSettingFieldItem

@Composable
fun MqttClientIdSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }

    val restricted = userSettings.isRestricted(UserSettingsKeys.Mqtt.Connection.CLIENT_ID)

    TextSettingFieldItem(
        label = "Client ID",
        infoText = """
            A unique identifier for this client when connecting to the MQTT broker.
        """.trimIndent(),
        placeholder = "e.g. webview-kiosk-01",
        initialValue = userSettings.mqttClientId,
        restricted = restricted,
        isMultiline = false,
        onSave = { userSettings.mqttClientId = it }
    )
}
