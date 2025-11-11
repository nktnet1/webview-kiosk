package uk.nktnet.webviewkiosk.ui.components.setting.fielditems.mqtt.connection

import android.content.ClipData
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.toClipEntry
import kotlinx.coroutines.launch
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

    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()

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
            if (it.trim().isEmpty()) {
                "(blank)"
            } else {
                MqttManager.mqttVariableReplacement(systemSettings, it)
            }
        },
        restricted = restricted,
        isMultiline = false,
        onLongClick = { v ->
            scope.launch {
                val clipData = ClipData.newPlainText(
                    "MQTT Client ID",
                    MqttManager.mqttVariableReplacement(systemSettings, v)
                )
                clipboard.setClipEntry(clipData.toClipEntry())
            }
        },
        onSave = { userSettings.mqttClientId = it },
    )
}
