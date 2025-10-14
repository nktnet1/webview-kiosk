package uk.nktnet.webviewkiosk.ui.components.setting.fielditems.webbrowsing

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.option.KioskControlPanelOption
import uk.nktnet.webviewkiosk.ui.components.setting.fields.DropdownSettingFieldItem

@Composable
fun KioskControlPanelSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }

    DropdownSettingFieldItem(
        label = "Kiosk Control Panel Region",
        infoText = """
            Controls which part of the screen can be tapped to show the Kiosk Control Panel
            when multi-tapping 10 times in quick succession.

            The default is top-left (upper-left quadrant of the screen).
        """.trimIndent(),
        options = KioskControlPanelOption.entries,
        initialValue = userSettings.allowKioskControlPanel,
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
