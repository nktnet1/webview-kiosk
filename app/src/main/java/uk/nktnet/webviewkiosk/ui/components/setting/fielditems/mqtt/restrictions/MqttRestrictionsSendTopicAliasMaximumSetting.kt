import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.UserSettingsKeys
import uk.nktnet.webviewkiosk.ui.components.setting.fields.NumberSettingFieldItem

@Composable
fun MqttRestrictionsSendTopicAliasMaximumSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }

    NumberSettingFieldItem(
        label = "Send Topic Alias Maximum",
        infoText = """
            Maximum number of topic aliases the client can send.
        """.trimIndent(),
        placeholder = "e.g. 16",
        initialValue = userSettings.mqttRestrictionsSendTopicAliasMaximum,
        restricted = userSettings.isRestricted(UserSettingsKeys.Mqtt.Restrictions.SEND_TOPIC_ALIAS_MAXIMUM),
        min = 0,
        max = 65_535,
        onSave = { userSettings.mqttRestrictionsSendTopicAliasMaximum = it }
    )
}
