package com.nktnet.webview_kiosk.ui.components.setting.fields.webengine

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.nktnet.webview_kiosk.config.UserSettings
import com.nktnet.webview_kiosk.ui.components.common.settings.fields.BooleanSettingFieldItem

@Composable
fun EnableDomStorageSetting() {
    val context = LocalContext.current
    val userSettings = UserSettings(context)

    BooleanSettingFieldItem(
        label = "Enable DOM Storage",
        infoText = "Allow web pages to use DOM storage APIs like local storage and session storage.",
        initialValue = userSettings.enableDomStorage,
        onSave = { userSettings.enableDomStorage = it }
    )
}
