package uk.nktnet.webviewkiosk.ui.components.setting.fielditems.weblifecycle

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import uk.nktnet.webviewkiosk.R
import uk.nktnet.webviewkiosk.config.Constants
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.UserSettingsKeys
import uk.nktnet.webviewkiosk.ui.components.setting.fields.NumberSettingFieldItem

@Composable
fun ResetOnInactivitySecondsSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }
    val settingKey = UserSettingsKeys.WebLifecycle.RESET_ON_INACTIVITY_SECONDS

    NumberSettingFieldItem(
        label =
            stringResource(R.string.web_lifecycle_reset_on_inactivity_seconds_title),
        infoText =
            stringResource(
                R.string.setting_info_reset_on_inactivity_seconds,
                Constants.MIN_INACTIVITY_TIMEOUT_SECONDS,
            ),
        placeholder = stringResource(R.string.ui_e_g_3600_for_1_hour),
        initialValue = userSettings.resetOnInactivitySeconds,
        settingKey = settingKey,
        restricted = userSettings.isRestricted(settingKey),
        min = Constants.MIN_INACTIVITY_TIMEOUT_SECONDS,
        max = Constants.MAX_INT_SETTING,
        onSave = { userSettings.resetOnInactivitySeconds = it },
    )
}
