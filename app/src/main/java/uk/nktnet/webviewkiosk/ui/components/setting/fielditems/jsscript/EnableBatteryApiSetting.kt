package com.nktnet.webview_kiosk.ui.components.setting.fielditems.jsscript

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.nktnet.webview_kiosk.R
import com.nktnet.webview_kiosk.config.UserSettings
import com.nktnet.webview_kiosk.config.UserSettingsKeys
import com.nktnet.webview_kiosk.ui.components.setting.fields.BooleanSettingFieldItem

@Composable
fun EnableBatteryApiSetting() {
    val context = LocalContext.current
    val userSettings = UserSettings(context)
    val settingKey = UserSettingsKeys.JsScripts.ENABLE_BATTERY_API

    BooleanSettingFieldItem(
        label = stringResource(id = R.string.js_scripts_enable_battery_api_title),
        infoText = """
            Allow web pages to access device battery status through a JavaScript
            interface. Web pages can use:

            JSON.parse(window.WebviewKioskBatteryInterface.getBatteryStatus())

            to retrieve battery level, charging status, temperature and other
            battery information.
        """.trimIndent(),
        initialValue = userSettings.enableBatteryApi,
        settingKey = settingKey,
        restricted = userSettings.isRestricted(settingKey),
        onSave = { userSettings.enableBatteryApi = it }
    )
}
