package uk.nktnet.webviewkiosk.ui.components.setting.fielditems.webbrowsing

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import uk.nktnet.webviewkiosk.R
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.UserSettingsKeys
import uk.nktnet.webviewkiosk.ui.components.setting.fields.BooleanSettingFieldItem

@Composable
fun AllowBookmarkAccessSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }

    BooleanSettingFieldItem(
        label = stringResource(id = R.string.web_browsing_allow_bookmark_access_title),
        infoText = "Whether the user can access saved bookmarks from the address bar.",
        initialValue = userSettings.allowBookmarkAccess,
        restricted = userSettings.isRestricted(UserSettingsKeys.WebBrowsing.ALLOW_BOOKMARK_ACCESS),
        onSave = { userSettings.allowBookmarkAccess = it }
    )
}
