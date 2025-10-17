package com.nktnet.webview_kiosk.ui.components.setting.fielditems.webbrowsing

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.nktnet.webview_kiosk.config.UserSettings
import com.nktnet.webview_kiosk.config.UserSettingsKeys
import com.nktnet.webview_kiosk.config.option.KioskControlPanelOption
import com.nktnet.webview_kiosk.ui.components.setting.fields.DropdownSettingFieldItem

@Composable
fun KioskControlPanelSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }

    DropdownSettingFieldItem(
        label = "Kiosk Control Panel Region",
        infoText = """
            Controls which part of the screen can be multi-tapped 10 times in quick
            succession to show the Kiosk Control Panel.

            The default is Top Left (upper-left quadrant of the screen).
        """.trimIndent(),
        options = KioskControlPanelOption.entries,
        initialValue = userSettings.allowKioskControlPanel,
        restricted = userSettings.isRestricted(UserSettingsKeys.WebBrowsing.ALLOW_KIOSK_CONTROL_PANEL),
        onSave = { userSettings.allowKioskControlPanel = it },
        itemText = {
            when (it) {
                KioskControlPanelOption.TOP_LEFT -> "Top Left"
                KioskControlPanelOption.TOP_RIGHT -> "Top Right"
                KioskControlPanelOption.BOTTOM_LEFT -> "Bottom Left"
                KioskControlPanelOption.BOTTOM_RIGHT -> "Bottom Right"
                KioskControlPanelOption.TOP -> "Top"
                KioskControlPanelOption.BOTTOM -> "Bottom"
                KioskControlPanelOption.FULL -> "Full Screen"
                KioskControlPanelOption.DISABLED -> "Disabled"
            }
        }
    )
}
