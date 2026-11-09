package uk.nktnet.webviewkiosk.ui.components.setting.fielditems.device.owner.locktaskfeature

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import uk.nktnet.webviewkiosk.R
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.UserSettingsKeys
import uk.nktnet.webviewkiosk.managers.DeviceOwnerManager
import uk.nktnet.webviewkiosk.ui.components.setting.fields.BooleanSettingFieldItem

@Composable
fun LockTaskFeatureNotificationsSetting(enabled: Boolean = true) {
    val context = LocalContext.current
    val userSettings = UserSettings(context)
    val settingKey = UserSettingsKeys.Device.Owner.LockTaskFeature.NOTIFICATIONS

    BooleanSettingFieldItem(
        label =
            stringResource(
                R.string.device_owner_lock_task_feature_notifications_title
            ),
        infoText =
            stringResource(R.string.setting_info_lock_task_feature_notifications),
        initialValue = userSettings.lockTaskFeatureNotifications,
        settingKey = settingKey,
        restricted = userSettings.isRestricted(settingKey) || !enabled,
        onSave = {
            if (
                enabled &&
                    DeviceOwnerManager.getCapabilities(context).lockTaskFeatures
            ) {
                userSettings.lockTaskFeatureNotifications = it
            }
        },
    )
}
