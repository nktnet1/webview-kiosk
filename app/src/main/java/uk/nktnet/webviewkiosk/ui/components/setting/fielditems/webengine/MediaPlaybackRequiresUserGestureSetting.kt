package com.nktnet.webview_kiosk.ui.components.setting.fielditems.webengine

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.nktnet.webview_kiosk.R
import com.nktnet.webview_kiosk.config.UserSettings
import com.nktnet.webview_kiosk.config.UserSettingsKeys
import com.nktnet.webview_kiosk.ui.components.setting.fields.BooleanSettingFieldItem

@Composable
fun MediaPlaybackRequiresUserGestureSetting() {
    val context = LocalContext.current
    val userSettings = UserSettings(context)
    val settingKey = UserSettingsKeys.WebEngine.MEDIA_PLAYBACK_REQUIRES_USER_GESTURE

    BooleanSettingFieldItem(
        label = stringResource(id = R.string.web_engine_media_playback_requires_user_gesture_title),
        infoText = """
            Sets whether the WebView requires a user gesture (e.g. tap) to play media.
        """.trimIndent(),
        initialValue = userSettings.mediaPlaybackRequiresUserGesture,
        settingKey = settingKey,
        restricted = userSettings.isRestricted(settingKey),
        onSave = { userSettings.mediaPlaybackRequiresUserGesture = it }
    )
}
