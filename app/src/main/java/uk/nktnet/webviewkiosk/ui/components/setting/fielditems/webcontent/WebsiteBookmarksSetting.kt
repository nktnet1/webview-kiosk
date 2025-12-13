package com.nktnet.webview_kiosk.ui.components.setting.fielditems.webcontent

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.nktnet.webview_kiosk.R
import com.nktnet.webview_kiosk.config.Constants
import com.nktnet.webview_kiosk.config.UserSettings
import com.nktnet.webview_kiosk.config.UserSettingsKeys
import com.nktnet.webview_kiosk.ui.components.setting.fields.TextSettingFieldItem
import com.nktnet.webview_kiosk.utils.validateUrl

@Composable
fun WebsiteBookmarksSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }
    val settingKey = UserSettingsKeys.WebContent.WEBSITE_BOOKMARKS

    TextSettingFieldItem(
        label = stringResource(id = R.string.web_content_website_bookmarks_title ),
        infoText = """
            Add your bookmark URLs, one per line.
            You can access the bookmarks using the address bar menu.
        """.trimIndent(),
        placeholder = """
            e.g.
            ${Constants.WEBSITE_URL}
            ${Constants.GITHUB_URL}
        """.trimIndent(),
        initialValue = userSettings.websiteBookmarks,
        settingKey = settingKey,
        restricted = userSettings.isRestricted(settingKey),
        isMultiline = true,
        validator = { input ->
            input.isEmpty() || input.lines().all { validateUrl(it.trim()) }
        },
        validationMessage = "Some lines contain invalid URLs",
        onSave = { input ->
            userSettings.websiteBookmarks = input
        }
    )
}
