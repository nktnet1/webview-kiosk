package uk.nktnet.webviewkiosk.ui.components.setting.fielditems.jsscript

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import uk.nktnet.webviewkiosk.R
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.UserSettingsKeys
import uk.nktnet.webviewkiosk.ui.components.setting.fields.BooleanSettingFieldItem

@Composable
fun EnableVibrationApiSetting() {
    val context = LocalContext.current
    val userSettings = UserSettings(context)
    val settingKey = UserSettingsKeys.JsScripts.ENABLE_VIBRATION_API

    BooleanSettingFieldItem(
        label = stringResource(R.string.js_scripts_enable_vibration_api_title),
        infoText = """
            Allow web pages to vibrate the device using navigator.vibrate(pattern).

            The pattern can be a duration in milliseconds or an array of alternating
            vibration and pause durations. A user interaction may be required by the
            WebView before vibration is allowed.
        """.trimIndent(),
        initialValue = userSettings.enableVibrationApi,
        settingKey = settingKey,
        restricted = userSettings.isRestricted(settingKey),
        onSave = { userSettings.enableVibrationApi = it }
    )
}
