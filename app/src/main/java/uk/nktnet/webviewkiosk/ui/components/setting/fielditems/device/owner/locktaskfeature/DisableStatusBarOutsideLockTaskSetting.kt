package uk.nktnet.webviewkiosk.ui.components.setting.fielditems.device.owner.locktaskfeature

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.UserSettingsKeys
import uk.nktnet.webviewkiosk.ui.components.setting.fields.BooleanSettingFieldItem
import uk.nktnet.webviewkiosk.utils.DeviceOwnerStatusBarPolicy

@Composable
fun DisableStatusBarOutsideLockTaskSetting(disabledReason: String?) {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }
    val settingKey =
        UserSettingsKeys.Device.Owner.DISABLE_STATUS_BAR_OUTSIDE_LOCK_TASK

    BooleanSettingFieldItem(
        initialValue = userSettings.disableStatusBarOutsideLockTask,
        settingKey = settingKey,
        restricted = userSettings.isRestricted(settingKey),
        disabledReason = disabledReason,
        onSave = { enabled ->
            if (disabledReason == null) {
                userSettings.disableStatusBarOutsideLockTask = enabled
                DeviceOwnerStatusBarPolicy.reconcile(context)
            }
        },
    )
}
