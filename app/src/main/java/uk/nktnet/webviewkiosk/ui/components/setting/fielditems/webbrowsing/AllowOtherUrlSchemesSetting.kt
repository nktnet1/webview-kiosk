package com.nktnet.webview_kiosk.ui.components.setting.fielditems.webbrowsing

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.nktnet.webview_kiosk.R
import com.nktnet.webview_kiosk.config.UserSettings
import com.nktnet.webview_kiosk.config.UserSettingsKeys
import com.nktnet.webview_kiosk.ui.components.setting.fields.BooleanSettingFieldItem

@Composable
fun AllowOtherUrlSchemesSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }
    val settingKey = UserSettingsKeys.WebBrowsing.ALLOW_OTHER_URL_SCHEMES

    BooleanSettingFieldItem(
        label = stringResource(id = R.string.web_browsing_allow_other_url_schemes_title),
        infoText = """
            Allow opening of non-http/https URL schemes such as
            'mailto:', 'sms:', 'tel:', 'intent:', 'spotify:', 'whatsapp:',
            etc in other apps.

            NOTE: This only works when in unlocked/unpinned mode.
        """.trimIndent(),
        initialValue = userSettings.allowOtherUrlSchemes,
        settingKey = settingKey,
        restricted = userSettings.isRestricted(settingKey),
        onSave = { userSettings.allowOtherUrlSchemes = it }
    )
}
