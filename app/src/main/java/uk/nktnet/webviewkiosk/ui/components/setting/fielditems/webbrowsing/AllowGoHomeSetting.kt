package com.nktnet.webview_kiosk.ui.components.setting.fielditems.webbrowsing

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.nktnet.webview_kiosk.R
import com.nktnet.webview_kiosk.config.UserSettings
import com.nktnet.webview_kiosk.config.UserSettingsKeys
import com.nktnet.webview_kiosk.ui.components.setting.fields.BooleanSettingFieldItem

@Composable
fun AllowGoHomeSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }
    val settingKey = UserSettingsKeys.WebBrowsing.ALLOW_GO_HOME

    BooleanSettingFieldItem(
        label = stringResource(id = R.string.web_browsing_allow_go_home_title),
        infoText = "Whether the user can return to the configured home page.",
        initialValue = userSettings.allowGoHome,
        settingKey = settingKey,
        restricted = userSettings.isRestricted(settingKey),
        onSave = { value ->
            userSettings.allowGoHome = value
        }
    )
}
