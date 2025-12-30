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
fun AllowRefreshSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }
    val settingKey = UserSettingsKeys.WebBrowsing.ALLOW_REFRESH

    BooleanSettingFieldItem(
        label = stringResource(R.string.web_browsing_allow_refresh_title),
        infoText = """
            Set to true to allow the user to refresh the page, e.g. using the
            - address bar actions
            - kiosk control panel
            - pull to refresh (can be configured separately)
        """.trimIndent(),
        settingKey = settingKey,
        restricted = userSettings.isRestricted(settingKey),
        initialValue = userSettings.allowRefresh,
        onSave = { userSettings.allowRefresh = it }
    )
}
