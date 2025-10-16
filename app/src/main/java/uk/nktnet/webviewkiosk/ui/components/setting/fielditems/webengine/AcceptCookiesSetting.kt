package uk.nktnet.webviewkiosk.ui.components.setting.fielditems.webengine

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.UserSettingsKeys
import uk.nktnet.webviewkiosk.ui.components.setting.fields.BooleanSettingFieldItem

@Composable
fun AcceptCookiesSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }

    BooleanSettingFieldItem(
        label = "Accept Cookies",
        infoText = "Allow websites to store and read cookies.",
        initialValue = userSettings.acceptCookies,
        restricted = userSettings.isRestricted(UserSettingsKeys.WebEngine.ACCEPT_COOKIES),
        onSave = { userSettings.acceptCookies = it }
    )
}
