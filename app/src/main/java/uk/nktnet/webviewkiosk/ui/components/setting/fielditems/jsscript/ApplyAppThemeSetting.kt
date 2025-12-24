package com.nktnet.webview_kiosk.ui.components.setting.fielditems.jsscript

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.nktnet.webview_kiosk.R
import com.nktnet.webview_kiosk.config.Constants
import com.nktnet.webview_kiosk.config.UserSettings
import com.nktnet.webview_kiosk.config.UserSettingsKeys
import com.nktnet.webview_kiosk.ui.components.setting.fields.BooleanSettingFieldItem

@Composable
fun ApplyAppThemeSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }
    val settingKey = UserSettingsKeys.JsScripts.APPLY_APP_THEME

    BooleanSettingFieldItem(
        label = stringResource(id = R.string.js_scripts_apply_app_theme_title),
        infoText = """
            This script injects JavaScript code that will set prefers-color-scheme
            according to your selected theme in ${Constants.APP_NAME} Appearance settings,
            thus keeping the Webpage's theme consistent with the App's theme.

            This script will run immediately "on page start".

            If the theme setting is "System", this script is a no-op (does nothing).
        """.trimIndent(),
        initialValue = userSettings.applyAppTheme,
        settingKey = settingKey,
        restricted = userSettings.isRestricted(settingKey),
        onSave = { userSettings.applyAppTheme = it }
    )
}
