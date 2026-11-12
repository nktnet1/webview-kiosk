package uk.nktnet.webviewkiosk.ui.components.setting.fielditems.mqtt.restrictions

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import uk.nktnet.webviewkiosk.R
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.UserSettingsKeys
import uk.nktnet.webviewkiosk.ui.components.setting.fields.NumberSettingFieldItem

@Composable
fun MqttRestrictionsMaximumPacketSizeSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }
    val settingKey = UserSettingsKeys.Mqtt.Restrictions.MAXIMUM_PACKET_SIZE

    NumberSettingFieldItem(
        placeholder = stringResource(R.string.ui_e_g_2048),
        initialValue = userSettings.mqttRestrictionsMaximumPacketSize,
        settingKey = settingKey,
        restricted = userSettings.isRestricted(settingKey),
        min = 0,
        max = 268_435_460,
        onSave = { userSettings.mqttRestrictionsMaximumPacketSize = it },
    )
}
