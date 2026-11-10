package uk.nktnet.webviewkiosk.ui.components.setting.fielditems.webengine

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import uk.nktnet.webviewkiosk.R
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.UserSettingsKeys
import uk.nktnet.webviewkiosk.ui.components.setting.fields.BooleanSettingFieldItem

@Composable
fun BackgroundMediaPlaybackSetting() {
    val userSettings = UserSettings(LocalContext.current)
    val settingKey = UserSettingsKeys.WebEngine.BACKGROUND_MEDIA_PLAYBACK

    BooleanSettingFieldItem(
        label = stringResource(R.string.web_engine_background_media_playback_title),
        infoText = stringResource(R.string.setting_info_background_media_playback),
        initialValue = userSettings.backgroundMediaPlayback,
        settingKey = settingKey,
        restricted = userSettings.isRestricted(settingKey),
        onSave = { userSettings.backgroundMediaPlayback = it },
    )
}
