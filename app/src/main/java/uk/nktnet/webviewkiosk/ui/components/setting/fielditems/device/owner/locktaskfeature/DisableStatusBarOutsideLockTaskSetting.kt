package uk.nktnet.webviewkiosk.ui.components.setting.fielditems.device.owner.locktaskfeature

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import uk.nktnet.webviewkiosk.R
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.UserSettingsKeys
import uk.nktnet.webviewkiosk.ui.components.setting.fields.BooleanSettingFieldItem
import uk.nktnet.webviewkiosk.utils.DeviceOwnerStatusBarPolicy

@Composable
fun DisableStatusBarOutsideLockTaskSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }
    val settingKey =
        UserSettingsKeys.Device.Owner.DISABLE_STATUS_BAR_OUTSIDE_LOCK_TASK

    BooleanSettingFieldItem(
        label =
            stringResource(
                R.string.device_owner_disable_status_bar_outside_lock_task_title
            ),
        infoText =
            """
            When Lock on Launch is enabled and the app is Device Owner, keep
            the status bar disabled if the OS unexpectedly exits lock task mode.
            Android applies this policy after lock task exits; the app's normal
            Unlock action restores the status bar.

            This restricts notification shade access, but does not disable
            Settings or prevent all system-level escapes. Requires Android 6+.
            """
                .trimIndent(),
        initialValue = userSettings.disableStatusBarOutsideLockTask,
        settingKey = settingKey,
        restricted = userSettings.isRestricted(settingKey),
        onSave = { enabled ->
            userSettings.disableStatusBarOutsideLockTask = enabled
            if (!enabled) {
                DeviceOwnerStatusBarPolicy.sync(context, false)
            } else if (
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                    userSettings.lockOnLaunch &&
                    (context.getSystemService(Context.ACTIVITY_SERVICE)
                            as ActivityManager)
                        .lockTaskModeState == ActivityManager.LOCK_TASK_MODE_LOCKED
            ) {
                DeviceOwnerStatusBarPolicy.sync(context, true)
            }
        },
    )
}
