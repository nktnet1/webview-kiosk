package uk.nktnet.webviewkiosk.ui.components.setting.fielditems.device.owner.dhizuku

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import uk.nktnet.webviewkiosk.R
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.UserSettingsKeys
import uk.nktnet.webviewkiosk.ui.components.setting.fields.BooleanSettingFieldItem

@Composable
fun DhizukuRequestPermissionOnLaunchSetting() {
    val context = LocalContext.current
    val userSettings = UserSettings(context)
    val settingKey =
        UserSettingsKeys.Device.Owner.Dhizuku.REQUEST_PERMISSION_ON_LAUNCH

    BooleanSettingFieldItem(
        label = stringResource(R.string.ui_request_permission_on_launch),
        infoText =
            stringResource(
                R.string.setting_info_dhizuku_request_permission_on_launch,
                stringResource(R.string.app_name),
            ),
        initialValue = userSettings.dhizukuRequestPermissionOnLaunch,
        settingKey = settingKey,
        restricted = userSettings.isRestricted(settingKey),
        onSave = { userSettings.dhizukuRequestPermissionOnLaunch = it },
    )
}
