package com.nktnet.webview_kiosk.ui.components.setting.fielditems.appearance

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.nktnet.webview_kiosk.config.UserSettings
import com.nktnet.webview_kiosk.config.UserSettingsKeys
import com.nktnet.webview_kiosk.config.option.ThemeOption
import com.nktnet.webview_kiosk.ui.components.setting.fields.DropdownSettingFieldItem

@Composable
fun ThemeSetting(themeState: MutableState<ThemeOption>) {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }

    DropdownSettingFieldItem(
        label = "Theme",
        infoText = """
            Select the app theme: System (default), Dark or Light.

            See also: Settings -> JS Scripts -> Apply App Theme .
        """.trimIndent(),
        options = ThemeOption.entries,
        initialValue = userSettings.theme,
        restricted = userSettings.isRestricted(UserSettingsKeys.Appearance.THEME),
        onSave = {
            userSettings.theme = it
            themeState.value = it
        },
        itemText = {
            when (it) {
                ThemeOption.SYSTEM -> "System"
                ThemeOption.DARK -> "Dark"
                ThemeOption.LIGHT -> "Light"
            }
        }
    )
}
