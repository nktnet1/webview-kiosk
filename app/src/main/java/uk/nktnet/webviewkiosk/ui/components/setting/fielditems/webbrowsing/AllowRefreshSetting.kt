package uk.nktnet.webviewkiosk.ui.components.setting.fielditems.webbrowsing

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.UserSettingsKeys
import uk.nktnet.webviewkiosk.ui.components.setting.fields.BooleanSettingFieldItem

@Composable
fun AllowRefreshSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }

    BooleanSettingFieldItem(
        label = "Allow Refresh",
        infoText = "Whether the user can pull down at the top 1/4 of a webpage to refresh.",
        restricted = userSettings.isRestricted(UserSettingsKeys.WebBrowsing.ALLOW_REFRESH),
        initialValue = userSettings.allowRefresh,
        onSave = { userSettings.allowRefresh = it }
    )
}
