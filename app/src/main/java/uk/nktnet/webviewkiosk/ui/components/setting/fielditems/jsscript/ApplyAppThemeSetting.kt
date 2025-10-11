package uk.nktnet.webviewkiosk.ui.components.setting.fielditems.jsscript

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import uk.nktnet.webviewkiosk.config.Constants
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.ui.components.setting.fields.BooleanSettingFieldItem

@Composable
fun ApplyAppThemeSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }

    BooleanSettingFieldItem(
        label = "Apply App Theme",
        infoText = """
            This script injects JavaScript code that will set prefers-color-scheme
            according to your selected theme in ${Constants.APP_NAME} Appearance settings,
            thus keeping the Webpage's theme consistent with the App's theme.
            
            This script will run immediately "on page start".

            If the theme setting is "System", this script is a no-op (does nothing).
        """.trimIndent(),
        initialValue = userSettings.applyAppTheme,
        onSave = { userSettings.applyAppTheme = it }
    )
}
