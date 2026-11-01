package uk.nktnet.webviewkiosk.ui.components.setting.fielditems.jsscript

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import uk.nktnet.webviewkiosk.R
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.UserSettingsKeys
import uk.nktnet.webviewkiosk.ui.components.setting.fields.BooleanSettingFieldItem

@Composable
fun ApplyAppThemeSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }
    val settingKey = UserSettingsKeys.JsScripts.APPLY_APP_THEME

    BooleanSettingFieldItem(
        label = stringResource(R.string.js_scripts_apply_app_theme_title),
        infoText =
            stringResource(
                R.string.setting_info_apply_app_theme,
                stringResource(R.string.app_name),
            ),
        initialValue = userSettings.applyAppTheme,
        settingKey = settingKey,
        restricted = userSettings.isRestricted(settingKey),
        onSave = { userSettings.applyAppTheme = it },
    )
}
