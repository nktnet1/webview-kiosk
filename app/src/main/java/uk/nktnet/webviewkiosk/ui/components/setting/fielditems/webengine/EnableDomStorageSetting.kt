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

    BooleanSettingFieldItem(
        label = "Enable DOM Storage",
        infoText = "Allow web pages to use DOM storage APIs like local storage and session storage.",
        initialValue = userSettings.enableDomStorage,
        restricted = userSettings.isRestricted(UserSettingsKeys.WebEngine.ENABLE_DOM_STORAGE),
        onSave = { userSettings.enableDomStorage = it }
    )
}
