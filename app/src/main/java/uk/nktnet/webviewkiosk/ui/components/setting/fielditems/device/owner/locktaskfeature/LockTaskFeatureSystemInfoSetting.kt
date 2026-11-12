package uk.nktnet.webviewkiosk.ui.components.setting.fielditems.device.owner.locktaskfeature

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.UserSettingsKeys
import uk.nktnet.webviewkiosk.managers.DeviceOwnerManager
import uk.nktnet.webviewkiosk.ui.components.setting.fields.BooleanSettingFieldItem

@Composable
fun LockTaskFeatureSystemInfoSetting(disabledReason: String?) {
    val context = LocalContext.current
    val userSettings = UserSettings(context)
    val settingKey = UserSettingsKeys.Device.Owner.LockTaskFeature.SYSTEM_INFO

    BooleanSettingFieldItem(
        initialValue = userSettings.lockTaskFeatureSystemInfo,
        settingKey = settingKey,
        restricted = userSettings.isRestricted(settingKey),
        disabledReason = disabledReason,
        onSave = {
            if (
                disabledReason == null &&
                    DeviceOwnerManager.getCapabilities(context).lockTaskFeatures
            ) {
                userSettings.lockTaskFeatureSystemInfo = it
            }
        },
    )
}
