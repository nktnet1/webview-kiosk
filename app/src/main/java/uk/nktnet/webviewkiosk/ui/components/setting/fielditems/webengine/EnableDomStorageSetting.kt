package uk.nktnet.webviewkiosk.ui.components.setting.fielditems.webengine

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.UserSettingsKeys
import uk.nktnet.webviewkiosk.ui.components.setting.fields.BooleanSettingFieldItem

@Composable
fun EnableDomStorageSetting() {
    val context = LocalContext.current
    val userSettings = UserSettings(context)
    val settingKey = UserSettingsKeys.WebEngine.ENABLE_DOM_STORAGE

    BooleanSettingFieldItem(
        initialValue = userSettings.enableDomStorage,
        settingKey = settingKey,
        restricted = userSettings.isRestricted(settingKey),
        onSave = { userSettings.enableDomStorage = it },
    )
}
