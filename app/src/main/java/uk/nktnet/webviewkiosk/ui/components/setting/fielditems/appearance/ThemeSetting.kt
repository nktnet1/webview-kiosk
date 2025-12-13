package com.nktnet.webview_kiosk.ui.components.setting.fielditems.appearance

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.nktnet.webview_kiosk.R
import com.nktnet.webview_kiosk.config.UserSettings
import com.nktnet.webview_kiosk.config.UserSettingsKeys
import com.nktnet.webview_kiosk.config.option.ThemeOption
import com.nktnet.webview_kiosk.states.ThemeStateSingleton
import com.nktnet.webview_kiosk.ui.components.setting.fields.DropdownSettingFieldItem

@Composable
fun ThemeSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }
    val settingKey = UserSettingsKeys.Appearance.THEME

    DropdownSettingFieldItem(
        label = stringResource(id = R.string.appearance_theme_title),
        infoText = """
            Select the app theme: System (default), Dark or Light.

            See also: Settings -> JS Scripts -> Apply App Theme .
        """.trimIndent(),
        options = ThemeOption.entries,
        initialValue = userSettings.theme,
        settingKey = settingKey,
        restricted = userSettings.isRestricted(settingKey),
        itemText = { it.label },
        onSave = {
            userSettings.theme = it
            ThemeStateSingleton.setTheme(it)
        },
    )
}
