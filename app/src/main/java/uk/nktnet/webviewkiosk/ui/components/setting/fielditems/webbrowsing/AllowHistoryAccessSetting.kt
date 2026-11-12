package uk.nktnet.webviewkiosk.ui.components.setting.fielditems.webbrowsing

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.UserSettingsKeys
import uk.nktnet.webviewkiosk.ui.components.setting.fields.BooleanSettingFieldItem

@Composable
fun AllowHistoryAccessSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }
    val settingKey = UserSettingsKeys.WebBrowsing.ALLOW_HISTORY_ACCESS

    BooleanSettingFieldItem(
        initialValue = userSettings.allowHistoryAccess,
        settingKey = settingKey,
        restricted = userSettings.isRestricted(settingKey),
        onSave = { userSettings.allowHistoryAccess = it },
    )
}
