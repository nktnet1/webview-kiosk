package com.nktnet.webview_kiosk.ui.components.setting.fielditems.webcontent

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.nktnet.webview_kiosk.config.UserSettings
import com.nktnet.webview_kiosk.ui.components.setting.fields.TextSettingFieldItem
import com.nktnet.webview_kiosk.utils.validateMultilineRegex

@Composable
fun BlacklistSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }

    TextSettingFieldItem(
        label = "Blacklist Regex",
        infoText = """
            Specify regular expressions (regex), one per line.
            Escaping is required for special characters in regex like '.' and '?'.
            
            These patterns also use partial matching.
            If you need strict control, anchor your regex with `^` and `$`.
            
            Whitelist patterns take precedence over blacklist patterns.
        """.trimIndent(),
        placeholder = """
            e.g.
                ^.*$
                ^https://.*\.?google\.com/?.*
        """.trimIndent(),
        initialValue = userSettings.websiteBlacklist,
        isMultiline = true,
        validator = { validateMultilineRegex(it) },
        onSave = { userSettings.websiteBlacklist = it }
    )
}
