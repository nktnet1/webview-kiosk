package com.nktnet.webview_kiosk.ui.components.setting.fielditems.webengine

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.nktnet.webview_kiosk.R
import com.nktnet.webview_kiosk.config.UserSettings
import com.nktnet.webview_kiosk.config.UserSettingsKeys
import com.nktnet.webview_kiosk.ui.components.setting.fields.BooleanSettingFieldItem

@Composable
fun EnableDomStorageSetting() {
    val context = LocalContext.current
    val userSettings = UserSettings(context)
    val settingKey = UserSettingsKeys.WebEngine.ENABLE_DOM_STORAGE

    BooleanSettingFieldItem(
        label = stringResource(id = R.string.web_engine_enable_dom_storage_title),
        infoText = "Allow web pages to use DOM storage APIs like local storage and session storage.",
        initialValue = userSettings.enableDomStorage,
        settingKey = settingKey,
        restricted = userSettings.isRestricted(settingKey),
        onSave = { userSettings.enableDomStorage = it }
    )
}
