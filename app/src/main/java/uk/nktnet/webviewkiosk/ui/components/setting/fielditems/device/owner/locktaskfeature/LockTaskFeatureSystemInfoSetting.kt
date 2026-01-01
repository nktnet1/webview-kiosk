package uk.nktnet.webviewkiosk.ui.components.setting.fielditems.device.owner.locktaskfeature

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import uk.nktnet.webviewkiosk.R
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.UserSettingsKeys
import uk.nktnet.webviewkiosk.ui.components.setting.fields.BooleanSettingFieldItem

@Composable
fun LockTaskFeatureSystemInfoSetting() {
    val context = LocalContext.current
    val userSettings = UserSettings(context)
    val settingKey = UserSettingsKeys.Device.Owner.LockTaskFeature.SYSTEM_INFO

    BooleanSettingFieldItem(
        label = stringResource(R.string.device_owner_lock_task_feature_system_info_title),
        infoText = """
            Enables the status bar's system info area that contains indicators
            such as connectivity, battery, and sound/vibrate options.
        """.trimIndent(),
        initialValue = userSettings.lockTaskFeatureSystemInfo,
        settingKey = settingKey,
        restricted = userSettings.isRestricted(settingKey),
        onSave = { userSettings.lockTaskFeatureSystemInfo = it },
    )
}
