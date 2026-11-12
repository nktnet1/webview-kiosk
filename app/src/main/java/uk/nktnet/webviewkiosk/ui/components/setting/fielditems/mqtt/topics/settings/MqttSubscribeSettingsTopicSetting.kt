package uk.nktnet.webviewkiosk.ui.components.setting.fielditems.mqtt.topics.settings

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
import uk.nktnet.webviewkiosk.utils.isValidMqttSubscribeTopic

@Composable
fun MqttSubscribeSettingsTopicSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }
    val settingKey = UserSettingsKeys.Mqtt.Topics.Subscribe.Settings.TOPIC

    TextSettingFieldItem(
        infoText =
            stringResource(
                settingDefinition(settingKey).descriptionRes,
                MqttVariableName.APP_INSTANCE_ID.name,
                MqttVariableName.USERNAME.name,
            ),
        placeholder = stringResource(R.string.ui_e_g_wk_settings),
        initialValue = userSettings.mqttSubscribeSettingsTopic,
        validator = { it.isEmpty() || isValidMqttSubscribeTopic(it) },
        descriptionFormatter = {
            mqttVariableReplacement(it)
        },
        settingKey = settingKey,
        restricted = userSettings.isRestricted(settingKey),
        isMultiline = false,
        onSave = { userSettings.mqttSubscribeSettingsTopic = it },
    )
}
