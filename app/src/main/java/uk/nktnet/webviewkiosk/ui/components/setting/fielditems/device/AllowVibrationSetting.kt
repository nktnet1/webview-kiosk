package uk.nktnet.webviewkiosk.ui.components.setting.fielditems.device

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import uk.nktnet.webviewkiosk.R
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.UserSettingsKeys
import uk.nktnet.webviewkiosk.ui.components.setting.fields.BooleanSettingFieldItem

@Composable
fun AllowVibrationSetting() {
    val context = LocalContext.current
    val userSettings = UserSettings(context)
    val settingKey = UserSettingsKeys.Device.ALLOW_VIBRATION

    BooleanSettingFieldItem(
        label = stringResource(R.string.device_allow_vibration_title),
        infoText = """
            Allow web pages to vibrate the device using navigator.vibrate(pattern).

            The pattern can be a duration in milliseconds or an array of alternating
            vibration and pause durations. A user interaction may be required by the
            WebView before vibration is allowed.
        """.trimIndent(),
        initialValue = userSettings.allowVibration,
        settingKey = settingKey,
        restricted = userSettings.isRestricted(settingKey),
        onSave = { userSettings.allowVibration = it }
    )
}
