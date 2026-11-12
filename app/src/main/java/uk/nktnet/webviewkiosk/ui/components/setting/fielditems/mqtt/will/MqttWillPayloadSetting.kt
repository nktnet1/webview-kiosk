package uk.nktnet.webviewkiosk.ui.components.setting.fielditems.mqtt.will

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import uk.nktnet.webviewkiosk.R
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.UserSettingsKeys
import uk.nktnet.webviewkiosk.config.mqtt.MqttVariableName
import uk.nktnet.webviewkiosk.managers.MqttManager.mqttVariableReplacement
import uk.nktnet.webviewkiosk.ui.components.setting.fields.TextSettingFieldItem
import uk.nktnet.webviewkiosk.ui.screens.settingDefinition

@Composable
fun MqttWillPayloadSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }
    val settingKey = UserSettingsKeys.Mqtt.Will.PAYLOAD

    TextSettingFieldItem(
        infoText =
            stringResource(
                settingDefinition(settingKey).descriptionRes,
                MqttVariableName.USERNAME.name,
                MqttVariableName.APP_INSTANCE_ID.name,
            ),
        placeholder =
            stringResource(
                R.string.ui_mqtt_will_payload_example,
                MqttVariableName.APP_INSTANCE_ID.name,
            ),
        initialValue = userSettings.mqttWillPayload,
        settingKey = settingKey,
        restricted = userSettings.isRestricted(settingKey),
        isMultiline = true,
        descriptionFormatter = {
            mqttVariableReplacement(it)
        },
        onSave = { userSettings.mqttWillPayload = it },
    )
}
