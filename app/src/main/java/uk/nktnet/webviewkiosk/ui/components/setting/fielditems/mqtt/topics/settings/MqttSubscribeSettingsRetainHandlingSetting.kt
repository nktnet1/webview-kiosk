package uk.nktnet.webviewkiosk.ui.components.setting.fielditems.mqtt.topics.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import uk.nktnet.webviewkiosk.R
import uk.nktnet.webviewkiosk.config.Constants
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.UserSettingsKeys
import uk.nktnet.webviewkiosk.config.option.MqttRetainHandlingOption
import uk.nktnet.webviewkiosk.ui.components.setting.fields.DropdownSettingFieldItem

@Composable
fun MqttSubscribeSettingsRetainHandlingSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }

    DropdownSettingFieldItem(
        label = stringResource(R.string.mqtt_subscribe_settings_retain_handling_title),
        infoText = """
            Control whether ${Constants.APP_NAME}} should receive existing
            retained messages when subscribing.
        """.trimIndent(),
        options = MqttRetainHandlingOption.entries,
        initialValue = userSettings.mqttSubscribeSettingsRetainHandling,
        restricted = userSettings.isRestricted(UserSettingsKeys.Mqtt.Topics.Subscribe.Settings.RETAIN_HANDLING),
        onSave = { userSettings.mqttSubscribeSettingsRetainHandling = it },
        itemText = { it.getSettingLabel() },
    )
}
