package uk.nktnet.webviewkiosk.ui.components.setting.fielditems.webbrowsing

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.UserSettingsKeys
import uk.nktnet.webviewkiosk.ui.components.setting.fields.BooleanSettingFieldItem

@Composable
fun AllowLinkLongPressContextMenuSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }

    BooleanSettingFieldItem(
        label = "Allow Link Long Press Context Menu",
        infoText = """
            When enabled, long-pressing links in the WebView will trigger
            a custom context menu.

            This will take precedence over the "Allow Default Long Press"
            setting (i.e. overrides it).

            This allows actions like opening or copying the link.
        """.trimIndent(),
        initialValue = userSettings.allowLinkLongPressContextMenu,
        restricted = userSettings.isRestricted(UserSettingsKeys.WebBrowsing.ALLOW_LINK_LONG_PRESS_CONTEXT_MENU),
        onSave = { userSettings.allowLinkLongPressContextMenu = it }
    )
}
