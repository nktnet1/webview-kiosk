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
fun ClearHistoryOnHomeSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }

    BooleanSettingFieldItem(
        label = stringResource(id = R.string.web_browsing_clear_history_on_home_title),
        infoText = "Clear the browser history whenever the user triggers an action to return home.",
        initialValue = userSettings.clearHistoryOnHome,
        restricted = userSettings.isRestricted(UserSettingsKeys.WebBrowsing.CLEAR_HISTORY_ON_HOME),
        onSave = { userSettings.clearHistoryOnHome = it }
    )
}
