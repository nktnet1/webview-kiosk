package com.nktnet.webview_kiosk.ui.components.setting.fielditems.appearance

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.nktnet.webview_kiosk.R
import com.nktnet.webview_kiosk.config.Constants
import com.nktnet.webview_kiosk.config.UserSettings
import com.nktnet.webview_kiosk.config.UserSettingsKeys
import com.nktnet.webview_kiosk.ui.components.setting.fields.TextSettingFieldItem

@Composable
fun CustomBlockPageHtmlSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }
    val settingKey = UserSettingsKeys.Appearance.CUSTOM_BLOCK_PAGE_HTML

    TextSettingFieldItem(
        label = stringResource(id = R.string.appearance_custom_block_page_html_title),
        infoText = """
            Define the custom HTML content to show when a page is blocked.

            Leave blank to use the default block page.
        """.trimIndent(),
        placeholder = """
            e.g.
                <div style="text-align:center; margin-top:10%;">
                  <h1 style="font-size:4rem;">Blocked</h1>
                  <p style="font-size:3rem;">This site is not accessible.</p>
                </div>

            or as a redirect:
                <meta http-equiv="refresh" content="0; url=${Constants.WEBSITE_URL}">
        """.trimIndent(),
        initialValue = userSettings.customBlockPageHtml,
        settingKey = settingKey,
        restricted = userSettings.isRestricted(settingKey),
        isMultiline = true,
        onSave = { userSettings.customBlockPageHtml = it },
    )
}
