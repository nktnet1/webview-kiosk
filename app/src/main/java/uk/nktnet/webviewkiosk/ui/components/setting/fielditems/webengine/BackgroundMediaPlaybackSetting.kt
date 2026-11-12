package uk.nktnet.webviewkiosk.ui.components.setting.fielditems.webengine

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.UserSettingsKeys
import uk.nktnet.webviewkiosk.ui.components.setting.fields.BooleanSettingFieldItem

@Composable
fun BackgroundMediaPlaybackSetting() {
    val context = LocalContext.current
    val userSettings = remember(context) { UserSettings(context) }
    val settingKey = UserSettingsKeys.WebEngine.BACKGROUND_MEDIA_PLAYBACK

    BooleanSettingFieldItem(
        initialValue = userSettings.backgroundMediaPlayback,
        settingKey = settingKey,
        restricted = userSettings.isRestricted(settingKey),
        onSave = { userSettings.backgroundMediaPlayback = it },
    )
}
