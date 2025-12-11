package uk.nktnet.webviewkiosk.ui.components.setting.fielditems.webengine

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import uk.nktnet.webviewkiosk.R
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.UserSettingsKeys
import uk.nktnet.webviewkiosk.ui.components.setting.fields.BooleanSettingFieldItem

@Composable
fun MediaPlaybackRequiresUserGestureSetting() {
    val context = LocalContext.current
    val userSettings = UserSettings(context)

    BooleanSettingFieldItem(
        label = stringResource(id = R.string.web_engine_media_playback_requires_user_gesture_title),
        infoText = """
            Sets whether the WebView requires a user gesture (e.g. tap) to play media.
        """.trimIndent(),
        initialValue = userSettings.mediaPlaybackRequiresUserGesture,
        restricted = userSettings.isRestricted(UserSettingsKeys.WebEngine.MEDIA_PLAYBACK_REQUIRES_USER_GESTURE),
        onSave = { userSettings.mediaPlaybackRequiresUserGesture = it }
    )
}
