package uk.nktnet.webviewkiosk.ui.components.setting.fielditems.weblifecycle

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.UserSettingsKeys
import uk.nktnet.webviewkiosk.ui.components.setting.fields.BooleanSettingFieldItem
import uk.nktnet.webviewkiosk.utils.LockTaskRecovery

@Composable
fun RelockOnUnexpectedExitSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }
    val settingKey = UserSettingsKeys.WebLifecycle.RELOCK_ON_UNEXPECTED_EXIT

    BooleanSettingFieldItem(
        initialValue = userSettings.relockOnUnexpectedExit,
        settingKey = settingKey,
        restricted = userSettings.isRestricted(settingKey),
        onSave = { enabled ->
            userSettings.relockOnUnexpectedExit = enabled
            LockTaskRecovery.onSettingsChanged(enabled, userSettings.lockOnLaunch)
        },
    )
}
