package uk.nktnet.webviewkiosk.ui.components.setting.fielditems.webcontent

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.UserSettingsKeys
import uk.nktnet.webviewkiosk.ui.components.setting.fields.BooleanSettingFieldItem

@Composable
fun EnableContentBlockingSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }
    val settingKey = UserSettingsKeys.WebContent.ENABLE_CONTENT_BLOCKING

    BooleanSettingFieldItem(
        initialValue = userSettings.enableContentBlocking,
        settingKey = settingKey,
        restricted = userSettings.isRestricted(settingKey),
        onSave = { userSettings.enableContentBlocking = it },
    )
}
