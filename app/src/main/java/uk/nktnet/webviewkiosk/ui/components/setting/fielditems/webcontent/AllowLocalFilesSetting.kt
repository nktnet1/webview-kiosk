package uk.nktnet.webviewkiosk.ui.components.setting.fielditems.webcontent

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.UserSettingsKeys
import uk.nktnet.webviewkiosk.ui.components.setting.fields.BooleanSettingFieldItem

@Composable
fun AllowLocalFilesSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }
    val settingKey = UserSettingsKeys.WebContent.ALLOW_LOCAL_FILES

    BooleanSettingFieldItem(
        initialValue = userSettings.allowLocalFiles,
        settingKey = settingKey,
        restricted = userSettings.isRestricted(settingKey),
        onSave = { userSettings.allowLocalFiles = it },
    )
}
