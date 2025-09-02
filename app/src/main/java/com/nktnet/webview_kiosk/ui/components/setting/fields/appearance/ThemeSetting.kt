package com.nktnet.webview_kiosk.ui.components.setting.fields.appearance

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.nktnet.webview_kiosk.config.UserSettings
import com.nktnet.webview_kiosk.config.option.ThemeOption
import com.nktnet.webview_kiosk.ui.components.common.settings.fields.DropdownSettingFieldItem

@Composable
fun ThemeSetting(themeState: MutableState<ThemeOption>) {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }

    DropdownSettingFieldItem(
        label = "Theme",
        infoText = """
            Select the app theme: System (default), Dark or Light.

            If either Dark or Light is selected, custom JavaScript will be injected to override the prefers-color-scheme property of the WebView page.
        """.trimIndent(),
        options = ThemeOption.entries,
        initialValue = userSettings.theme,
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
