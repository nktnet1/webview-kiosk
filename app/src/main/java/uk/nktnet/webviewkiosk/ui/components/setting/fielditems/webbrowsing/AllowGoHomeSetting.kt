package uk.nktnet.webviewkiosk.ui.components.setting.fielditems.webbrowsing

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.UserSettingsKeys
import uk.nktnet.webviewkiosk.ui.components.setting.fields.BooleanSettingFieldItem

@Composable
fun AllowGoHomeSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }

    BooleanSettingFieldItem(
        label = "Allow Go Home",
        infoText = "Whether the user can return to the configured home page.",
        initialValue = userSettings.allowGoHome,
        restricted = userSettings.isRestricted(UserSettingsKeys.WebBrowsing.ALLOW_GO_HOME),
        onSave = { value ->
            userSettings.allowGoHome = value
        }
    )
}
