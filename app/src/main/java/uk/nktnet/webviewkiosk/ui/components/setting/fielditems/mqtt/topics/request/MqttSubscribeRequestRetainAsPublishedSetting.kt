package uk.nktnet.webviewkiosk.ui.components.setting.fielditems.mqtt.topics.request

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import uk.nktnet.webviewkiosk.R
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.UserSettingsKeys
import uk.nktnet.webviewkiosk.ui.components.setting.fields.BooleanSettingFieldItem

@Composable
fun MqttSubscribeRequestRetainAsPublishedSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }
    val settingKey =
        UserSettingsKeys.Mqtt.Topics.Subscribe.Request.RETAIN_AS_PUBLISHED

    BooleanSettingFieldItem(
        label =
            stringResource(
                R.string.mqtt_subscribe_request_retain_as_published_title
            ),
        infoText =
            stringResource(
                R.string.setting_info_mqtt_subscribe_request_retain_as_published
            ),
        initialValue = userSettings.mqttSubscribeRequestRetainAsPublished,
        settingKey = settingKey,
        restricted = userSettings.isRestricted(settingKey),
        onSave = { userSettings.mqttSubscribeRequestRetainAsPublished = it },
    )
}
