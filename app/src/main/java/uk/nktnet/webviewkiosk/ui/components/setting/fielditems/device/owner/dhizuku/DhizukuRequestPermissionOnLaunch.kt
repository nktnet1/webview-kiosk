package uk.nktnet.webviewkiosk.ui.components.setting.fielditems.device.owner.dhizuku

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import uk.nktnet.webviewkiosk.config.Constants
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.UserSettingsKeys
import uk.nktnet.webviewkiosk.ui.components.setting.fields.BooleanSettingFieldItem

@Composable
fun DhizukuRequestPermissionOnLaunch() {
    val context = LocalContext.current
    val userSettings = UserSettings(context)

    BooleanSettingFieldItem(
        label = "Request Permission on Launch",
        infoText = """
            When enabled, if Dhizuku is installed and has not granted ${Constants.APP_NAME}
            access to Device Owner privileges, prompt the user for permission.
        """.trimIndent(),
        initialValue = userSettings.dhizukuRequestPermissionOnLaunch,
        restricted = userSettings.isRestricted(
            UserSettingsKeys.Device.Owner.Dhizuku.REQUEST_PERMISSION_ON_LAUNCH
        ),
        onSave = { userSettings.dhizukuRequestPermissionOnLaunch = it },
    )
}
