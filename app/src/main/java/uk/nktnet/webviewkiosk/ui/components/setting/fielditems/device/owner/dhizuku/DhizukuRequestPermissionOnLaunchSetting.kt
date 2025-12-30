package uk.nktnet.webviewkiosk.ui.components.setting.fielditems.device.owner.dhizuku

import androidx.compose.runtime.*
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
    val settingKey = UserSettingsKeys.Device.Owner.Dhizuku.REQUEST_PERMISSION_ON_LAUNCH

    BooleanSettingFieldItem(
        label = "Request Permission on Launch",
        infoText = """
            When enabled, if Dhizuku is installed and has not granted
            ${stringResource(R.string.app_name)} access to Device
            Owner privileges, prompt the user for permission.
        """.trimIndent(),
        initialValue = userSettings.dhizukuRequestPermissionOnLaunch,
        settingKey = settingKey,
        restricted = userSettings.isRestricted(settingKey),
        onSave = { userSettings.dhizukuRequestPermissionOnLaunch = it },
    )
}
