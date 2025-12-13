package uk.nktnet.webviewkiosk.ui.components.setting.fielditems.device.owner.locktaskfeature

import androidx.compose.runtime.*
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

    BooleanSettingFieldItem(
        label = stringResource(id = R.string.device_owner_lock_task_feature_block_activity_start_in_task_title),
        infoText = """
            Enable blocking of non-allowlisted activities from being started
            into a locked task.

            This requires Android 11 (API Level 30).
        """.trimIndent(),
        initialValue = userSettings.lockTaskFeatureBlockActivityStartInTask,
        restricted = userSettings.isRestricted(UserSettingsKeys.Device.Owner.LockTaskFeature.BLOCK_ACTIVITY_START_IN_TASK),
        onSave = { userSettings.lockTaskFeatureBlockActivityStartInTask = it },
    )
}
