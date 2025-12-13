package com.nktnet.webview_kiosk.ui.components.setting.fielditems.webengine

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.nktnet.webview_kiosk.R
import com.nktnet.webview_kiosk.config.UserSettings
import com.nktnet.webview_kiosk.config.UserSettingsKeys
import com.nktnet.webview_kiosk.config.option.OverScrollModeOption
import com.nktnet.webview_kiosk.ui.components.setting.fields.DropdownSettingFieldItem

@Composable
fun OverScrollModeSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }
    val settingKey = UserSettingsKeys.WebEngine.OVER_SCROLL_MODE

    DropdownSettingFieldItem(
        label = stringResource(id = R.string.web_engine_over_scroll_mode_title),
        infoText = """
            Configures the WebView's overscroll behavior.

            Options:
            - Always: Shows the overscroll effect whenever the content is scrolled.
            - If Content Scrolls: allow over-scrolling only if the view content is larger than the container
            - Never: Disables the overscroll effect entirely.
        """.trimIndent(),
        options = OverScrollModeOption.entries,
        initialValue = userSettings.overScrollMode,
        settingKey = settingKey,
        restricted = userSettings.isRestricted(settingKey),
        onSave = { userSettings.overScrollMode = it },
        itemText = { it.label },
    )
}
