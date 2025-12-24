package com.nktnet.webview_kiosk.ui.components.setting.fielditems.webbrowsing

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.nktnet.webview_kiosk.R
import com.nktnet.webview_kiosk.config.UserSettings
import com.nktnet.webview_kiosk.config.UserSettingsKeys
import com.nktnet.webview_kiosk.config.option.OverrideUrlLoadingBlockActionOption
import com.nktnet.webview_kiosk.ui.components.setting.fields.DropdownSettingFieldItem

@Composable
fun OverrideUrlLoadingBlockActionSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }
    val settingKey = UserSettingsKeys.WebBrowsing.OVERRIDE_URL_LOADING_BLOCK_ACTION

    DropdownSettingFieldItem(
        label = stringResource(id = R.string.web_browsing_override_url_loading_block_action_title),
        infoText = """
            Action to take when a URL navigation (via shouldOverrideUrlLoading)
            matches the blacklist regex.

            Typically, this is from clicking links (<a> tags) on a webpage's HTML.

            Actions are:
            - Show Block Page (display a HTML block page)
            - Prevent Navigation (does nothing when links are clicked)
            - Show Toast (shows a toast with your custom block message)

            This option will NOT take effect for:
            - JavaScript navigation
            - Custom URL loading, e.g. from the Address Bar, Bookmarks, History, etc

            In those other cases, the block page will simply be shown.
        """.trimIndent(),
        options = OverrideUrlLoadingBlockActionOption.entries,
        settingKey = settingKey,
        restricted = userSettings.isRestricted(settingKey),
        initialValue = userSettings.overrideUrlLoadingBlockAction,
        onSave = { userSettings.overrideUrlLoadingBlockAction = it },
        itemText = { it.label },
    )
}
