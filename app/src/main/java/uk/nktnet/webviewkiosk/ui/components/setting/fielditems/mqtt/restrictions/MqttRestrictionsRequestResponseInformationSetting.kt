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
fun MqttRestrictionsRequestResponseInformationSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }

    BooleanSettingFieldItem(
        label = stringResource(R.string.mqtt_restrictions_request_response_information_title),
        infoText = """
            When enabled, the client requests additional response information
            from the broker in MQTT responses.
        """.trimIndent(),
        initialValue = userSettings.mqttRestrictionsRequestResponseInformation,
        restricted = userSettings.isRestricted(UserSettingsKeys.Mqtt.Restrictions.REQUEST_RESPONSE_INFORMATION),
        onSave = { userSettings.mqttRestrictionsRequestResponseInformation = it }
    )
}
