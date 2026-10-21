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
fun RefreshOnScreenWakeSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }
    val settingKey = UserSettingsKeys.WebLifecycle.REFRESH_ON_SCREEN_WAKE

    BooleanSettingFieldItem(
        label = stringResource(R.string.web_lifecycle_refresh_on_screen_wake_title),
        infoText =
            """
            Refresh the current page once when the device wakes from sleep and the
            kiosk returns to the foreground. This can restore dashboard connections
            that became stale while the device was asleep.

            Opening settings, rotating the screen or returning from another app
            does not trigger a refresh. Requires Allow Refresh to be enabled.
            """
                .trimIndent(),
        initialValue = userSettings.refreshOnScreenWake,
        settingKey = settingKey,
        restricted = userSettings.isRestricted(settingKey),
        onSave = { userSettings.refreshOnScreenWake = it },
    )
}
