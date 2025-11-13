package uk.nktnet.webviewkiosk.ui.components.setting.fielditems.mqtt.restrictions

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.UserSettingsKeys
import uk.nktnet.webviewkiosk.ui.components.setting.fields.BooleanSettingFieldItem

@Composable
fun MqttRestrictionsRequestProblemInformationSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }

    BooleanSettingFieldItem(
        label = "Request Problem Information",
        infoText = """
            When enabled, the client requests additional problem information
            from the broker in MQTT responses.
        """.trimIndent(),
        initialValue = userSettings.mqttRestrictionsRequestProblemInformation,
        restricted = userSettings.isRestricted(UserSettingsKeys.Mqtt.Restrictions.REQUEST_PROBLEM_INFORMATION),
        onSave = { userSettings.mqttRestrictionsRequestProblemInformation = it }
    )
}
