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
fun AllowGoHomeSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }

    BooleanSettingFieldItem(
        label = stringResource(id = R.string.web_browsing_allow_go_home_title),
        infoText = "Whether the user can return to the configured home page.",
        initialValue = userSettings.allowGoHome,
        restricted = userSettings.isRestricted(UserSettingsKeys.WebBrowsing.ALLOW_GO_HOME),
        onSave = { value ->
            userSettings.allowGoHome = value
        }
    )
}
