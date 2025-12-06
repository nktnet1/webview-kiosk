package com.nktnet.webview_kiosk.ui.components.setting.fielditems.jsscript

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.nktnet.webview_kiosk.config.UserSettings
import com.nktnet.webview_kiosk.config.UserSettingsKeys
import com.nktnet.webview_kiosk.ui.components.setting.fields.BooleanSettingFieldItem

@Composable
fun EnableBrightnessApiSetting() {
    val context = LocalContext.current
    val userSettings = UserSettings(context)

    BooleanSettingFieldItem(
        label = "Enable Brightness API",
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
        restricted = userSettings.isRestricted(UserSettingsKeys.JsScripts.ENABLE_BRIGHTNESS_API),
        onSave = { userSettings.enableBrightnessApi = it }
    )
}
