package uk.nktnet.webviewkiosk.ui.components.setting.fielditems.webengine

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.ui.components.setting.fields.BooleanSettingFieldItem

@Composable
fun MediaPlaybackRequiresUserGestureSetting() {
    val context = LocalContext.current
    val userSettings = UserSettings(context)

    BooleanSettingFieldItem(
        label = "Require User Gesture for Media Playback",
        infoText = "Sets whether the WebView requires a user gesture (e.g. tap) to play media.",
        initialValue = userSettings.mediaPlaybackRequiresUserGesture,
        onSave = { userSettings.mediaPlaybackRequiresUserGesture = it }
    )
}
