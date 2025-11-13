package uk.nktnet.webviewkiosk.ui.components.setting.fielditems.mqtt.restrictions

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.UserSettingsKeys
import uk.nktnet.webviewkiosk.ui.components.setting.fields.NumberSettingFieldItem

@Composable
fun MqttRestrictionsTopicAliasMaximumSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }

    NumberSettingFieldItem(
        label = "Topic Alias Maximum",
        infoText = """
            Maximum number of topic aliases the client can receive.
        """.trimIndent(),
        placeholder = "e.g. 0",
        initialValue = userSettings.mqttRestrictionsTopicAliasMaximum,
        restricted = userSettings.isRestricted(UserSettingsKeys.Mqtt.Restrictions.TOPIC_ALIAS_MAXIMUM),
        min = 0,
        max = 65_535,
        onSave = { userSettings.mqttRestrictionsTopicAliasMaximum = it }
    )
}
