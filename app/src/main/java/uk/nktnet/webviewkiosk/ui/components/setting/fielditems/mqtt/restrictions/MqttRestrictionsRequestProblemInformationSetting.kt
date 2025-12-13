package uk.nktnet.webviewkiosk.ui.components.setting.fielditems.mqtt.restrictions

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import uk.nktnet.webviewkiosk.R
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.UserSettingsKeys
import uk.nktnet.webviewkiosk.ui.components.setting.fields.BooleanSettingFieldItem

@Composable
fun MqttRestrictionsRequestProblemInformationSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }

    BooleanSettingFieldItem(
        label = stringResource(R.string.mqtt_restrictions_request_problem_information_title),
        infoText = """
            When enabled, the client requests additional problem information
            from the broker in MQTT responses.
        """.trimIndent(),
        initialValue = userSettings.mqttRestrictionsRequestProblemInformation,
        restricted = userSettings.isRestricted(UserSettingsKeys.Mqtt.Restrictions.REQUEST_PROBLEM_INFORMATION),
        onSave = { userSettings.mqttRestrictionsRequestProblemInformation = it }
    )
}
