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
import uk.nktnet.webviewkiosk.utils.isValidMqttPublishTopic

@Composable
fun MqttWillTopicSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }
    val settingKey = UserSettingsKeys.Mqtt.Will.TOPIC

    TextSettingFieldItem(
        infoText =
            stringResource(
                settingDefinition(settingKey).descriptionRes,
                MqttVariableName.USERNAME.name,
                MqttVariableName.APP_INSTANCE_ID.name,
            ),
        placeholder = stringResource(R.string.ui_e_g_wk_will),
        initialValue = userSettings.mqttWillTopic,
        settingKey = settingKey,
        restricted = userSettings.isRestricted(settingKey),
        validator = {
            it.isEmpty() ||
                isValidMqttPublishTopic(
                    mqttVariableReplacement(
                        it,
                        mapOf(
                            MqttVariableName.USERNAME.name to
                                userSettings.mqttUsername
                        ),
                    )
                )
        },
        descriptionFormatter = {
            mqttVariableReplacement(it)
        },
        isMultiline = false,
        onSave = { userSettings.mqttWillTopic = it },
    )
}
