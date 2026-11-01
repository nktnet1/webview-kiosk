package uk.nktnet.webviewkiosk.ui.components.setting.fielditems.jsscript

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import uk.nktnet.webviewkiosk.R
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.UserSettingsKeys
import uk.nktnet.webviewkiosk.ui.components.setting.fields.BooleanSettingFieldItem

@Composable
fun EnableErudaConsoleSetting() {
    val context = LocalContext.current
    val userSettings = UserSettings(context)
    val settingKey = UserSettingsKeys.JsScripts.ENABLE_ERUDA_CONSOLE

    BooleanSettingFieldItem(
        label = stringResource(R.string.js_scripts_enable_eruda_console_title),
        infoText = stringResource(R.string.setting_info_enable_eruda_console),
        initialValue = userSettings.enableErudaConsole,
        settingKey = settingKey,
        restricted = userSettings.isRestricted(settingKey),
        onSave = { userSettings.enableErudaConsole = it },
    )
}
