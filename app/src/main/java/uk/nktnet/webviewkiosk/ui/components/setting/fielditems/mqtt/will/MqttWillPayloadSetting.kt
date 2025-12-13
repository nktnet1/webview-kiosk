package uk.nktnet.webviewkiosk.ui.components.setting.fielditems.mqtt.will

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import uk.nktnet.webviewkiosk.R
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.UserSettingsKeys
import uk.nktnet.webviewkiosk.config.option.MqttVariableNameOption
import uk.nktnet.webviewkiosk.managers.MqttManager.mqttVariableReplacement
import uk.nktnet.webviewkiosk.ui.components.setting.fields.TextSettingFieldItem

@Composable
fun MqttWillPayloadSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }
    val settingKey = UserSettingsKeys.Mqtt.Will.PAYLOAD

    TextSettingFieldItem(
        label = stringResource(R.string.mqtt_will_payload_title),
        infoText = """
            The MQTT payload to send for the last will message if the client
            disconnects unexpectedly.

            For example,
              {
                "message": "Client has disconnected.",
                "username": "${'$'}{${MqttVariableNameOption.USERNAME.name}}",
                "appInstanceId": "${'$'}{${MqttVariableNameOption.APP_INSTANCE_ID.name}}"
              }
        """.trimIndent(),
        placeholder = """
            {
              "message": "Client has disconnected.",
              "appInstanceId": "${'$'}{${MqttVariableNameOption.APP_INSTANCE_ID.name}}"
            }
        """.trimIndent(),
        initialValue = userSettings.mqttWillPayload,
        settingKey = settingKey,
        restricted = userSettings.isRestricted(settingKey),
        isMultiline = true,
        descriptionFormatter = {
            mqttVariableReplacement( it)
        },
        onSave = { userSettings.mqttWillPayload = it },
    )
}
