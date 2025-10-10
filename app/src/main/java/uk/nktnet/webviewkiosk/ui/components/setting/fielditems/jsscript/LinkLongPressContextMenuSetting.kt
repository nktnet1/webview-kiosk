package uk.nktnet.webviewkiosk.ui.components.setting.fielditems.jsscript

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.ui.components.setting.fields.BooleanSettingFieldItem

@Composable
fun LinkLongPressContextMenuSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }

    BooleanSettingFieldItem(
        label = "Enable Link Long Press Context Menu",
        infoText = """
            When enabled, long-pressing links in the WebView will trigger
            a custom context menu.

            This allows actions like opening or copying the link.
        """.trimIndent(),
        initialValue = userSettings.enableLinkLongPressContextMenu,
        onSave = { userSettings.enableLinkLongPressContextMenu = it }
    )
}
