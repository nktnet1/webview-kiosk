package uk.nktnet.webviewkiosk.ui.components.setting.fielditems.webbrowsing

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.UserSettingsKeys
import uk.nktnet.webviewkiosk.ui.components.setting.fields.BooleanSettingFieldItem

@Composable
fun AllowBackwardsNavigationSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }

    BooleanSettingFieldItem(
        label = "Allow Backwards Navigation",
        infoText = "Whether the user can use the device 'back' button to go back one page in history.",
        initialValue = userSettings.allowBackwardsNavigation,
        restricted = userSettings.isRestricted(UserSettingsKeys.WebBrowsing.ALLOW_BACKWARDS_NAVIGATION),
        onSave = { userSettings.allowBackwardsNavigation = it }
    )
}
