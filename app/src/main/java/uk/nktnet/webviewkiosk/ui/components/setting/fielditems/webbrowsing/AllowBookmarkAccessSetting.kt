package uk.nktnet.webviewkiosk.ui.components.setting.fielditems.webbrowsing

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.ui.components.setting.fields.BooleanSettingFieldItem

@Composable
fun AllowBookmarkAccessSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }

    BooleanSettingFieldItem(
        label = "Allow Bookmark Access",
        infoText = "Whether the user can access saved bookmarks from the address bar.",
        initialValue = userSettings.allowBookmarkAccess,
        onSave = { userSettings.allowBookmarkAccess = it }
    )
}
