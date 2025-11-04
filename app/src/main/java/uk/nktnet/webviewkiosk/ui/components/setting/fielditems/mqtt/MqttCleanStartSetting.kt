package uk.nktnet.webviewkiosk.ui.components.setting.fielditems.mqtt

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.UserSettingsKeys
import uk.nktnet.webviewkiosk.ui.components.setting.fields.BooleanSettingFieldItem

@Composable
fun MqttCleanStartSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }

    BooleanSettingFieldItem(
        label = "Clean Start",
        infoText = """
            When enabled, the MQTT client will start a new session on connect,
            discarding any previous session state stored by the broker.

            When disabled, the client will resume the previous session
            (subscriptions, in-flight messages, etc.) if it exists.
        """.trimIndent(),
        initialValue = userSettings.mqttCleanStart,
        restricted = userSettings.isRestricted(UserSettingsKeys.Mqtt.CLEAN_START),
        onSave = { userSettings.mqttCleanStart = it }
    )
}
