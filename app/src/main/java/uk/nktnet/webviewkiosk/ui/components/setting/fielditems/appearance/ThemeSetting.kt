package uk.nktnet.webviewkiosk.ui.components.setting.fielditems.appearance

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.UserSettingsKeys
import uk.nktnet.webviewkiosk.config.option.ThemeOption
import uk.nktnet.webviewkiosk.ui.components.setting.fields.DropdownSettingFieldItem

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
