package uk.nktnet.webviewkiosk.ui.components.setting.fielditems.jsscript

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import uk.nktnet.webviewkiosk.R
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.UserSettingsKeys
import uk.nktnet.webviewkiosk.ui.components.setting.fields.BooleanSettingFieldItem

@Composable
fun EnableBrightnessApiSetting() {
    val context = LocalContext.current
    val userSettings = UserSettings(context)
    val settingKey = UserSettingsKeys.JsScripts.ENABLE_BRIGHTNESS_API

    BooleanSettingFieldItem(
        label = stringResource(R.string.js_scripts_enable_brightness_api_title),
        infoText = """
            Allow web pages to use:

            1. window.WebviewKioskBrightnessInterface.getBrightness(): number
            2. window.WebviewKioskBrightnessInterface.setBrightness(value: number)

            to read or change the current screen brightness percentage.

            Values are integers between 0-100, with
               -1: use system brightness
                0: very dim
              100: very bright
        """.trimIndent(),
        initialValue = userSettings.enableBrightnessApi,
        settingKey = settingKey,
        restricted = userSettings.isRestricted(settingKey),
        onSave = { userSettings.enableBrightnessApi = it }
    )
}
