package uk.nktnet.webviewkiosk.ui.components.setting.fielditems.weblifecycle

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import uk.nktnet.webviewkiosk.R
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.UserSettingsKeys
import uk.nktnet.webviewkiosk.ui.components.setting.fields.BooleanSettingFieldItem

@Composable
fun RelockOnUnexpectedExitSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }
    val settingKey = UserSettingsKeys.WebLifecycle.RELOCK_ON_UNEXPECTED_EXIT

    BooleanSettingFieldItem(
        label =
            stringResource(R.string.web_lifecycle_relock_on_unexpected_exit_title),
        infoText =
            """
            When Lock on Launch is enabled and the app is Device Owner, check
            periodically while the kiosk activity is visible for unexpected exits
            from full lock task mode. Re-lock automatically unless the app's
            Unlock action was used.

            This does not prevent a firmware-level escape, run in the background,
            or replace proper lock task allowlisting. Screen pinning is excluded.
            """
                .trimIndent(),
        initialValue = userSettings.relockOnUnexpectedExit,
        settingKey = settingKey,
        restricted = userSettings.isRestricted(settingKey),
        onSave = { userSettings.relockOnUnexpectedExit = it },
    )
}
