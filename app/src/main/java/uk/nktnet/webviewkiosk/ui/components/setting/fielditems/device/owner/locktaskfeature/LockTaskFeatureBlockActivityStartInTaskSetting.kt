package uk.nktnet.webviewkiosk.ui.components.setting.fielditems.device.owner.locktaskfeature

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import uk.nktnet.webviewkiosk.R
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.UserSettingsKeys
import uk.nktnet.webviewkiosk.ui.components.setting.fields.BooleanSettingFieldItem

@Composable
fun LockTaskFeatureBlockActivityStartInTaskSetting() {
    val context = LocalContext.current
    val userSettings = UserSettings(context)
    val settingKey =
        UserSettingsKeys.Device.Owner.LockTaskFeature.BLOCK_ACTIVITY_START_IN_TASK

    BooleanSettingFieldItem(
        label =
            stringResource(
                R.string
                    .device_owner_lock_task_feature_block_activity_start_in_task_title
            ),
        infoText =
            stringResource(
                R.string.setting_info_lock_task_feature_block_activity_start_in_task
            ),
        initialValue = userSettings.lockTaskFeatureBlockActivityStartInTask,
        settingKey = settingKey,
        restricted = userSettings.isRestricted(settingKey),
        onSave = { userSettings.lockTaskFeatureBlockActivityStartInTask = it },
    )
}
