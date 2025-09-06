package com.nktnet.webview_kiosk.ui.components.setting.fielditems.webcontent

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.nktnet.webview_kiosk.config.Constants
import com.nktnet.webview_kiosk.config.UserSettings
import com.nktnet.webview_kiosk.ui.components.setting.fields.TextSettingFieldItem
import com.nktnet.webview_kiosk.utils.validateUrl

@Composable
fun BookmarksSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }

    var currentValue by remember { mutableStateOf(userSettings.websiteBookmarks) }

    TextSettingFieldItem(
        label = "Bookmarks",
        infoText = """
            Add your bookmark URLs, one per line.
            You can access the bookmarks using the address bar menu.
        """.trimIndent(),
        placeholder = """
            e.g.
            ${Constants.WEBSITE_URL}
            ${Constants.GITHUB_URL}
        """.trimIndent(),
        initialValue = currentValue,
        isMultiline = true,
        validator = { input ->
            input.lines().all { validateUrl(it.trim()) }
        },
        onSave = { input ->
            currentValue = input
            userSettings.websiteBookmarks = input
        }
    )
}
