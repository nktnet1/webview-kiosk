package uk.nktnet.webviewkiosk.ui.components.setting.fielditems.webbrowsing

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.UserSettingsKeys
import uk.nktnet.webviewkiosk.config.option.KioskControlPanelRegionOption
import uk.nktnet.webviewkiosk.ui.components.setting.fields.DropdownSettingFieldItem

@Composable
fun KioskControlPanelRegionSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }

    DropdownSettingFieldItem(
        label = "Kiosk Control Panel Region",
        infoText = """
            Controls which part of the screen can be multi-tapped 10 times in quick
            succession to show the Kiosk Control Panel.

            The default is Top Left (upper-left quadrant of the screen).
        """.trimIndent(),
        options = KioskControlPanelRegionOption.entries,
        initialValue = userSettings.kioskControlPanelRegion,
        restricted = userSettings.isRestricted(UserSettingsKeys.WebBrowsing.KIOSK_CONTROL_PANEL_REGION),
        onSave = { userSettings.kioskControlPanelRegion = it },
        itemText = {
            when (it) {
                KioskControlPanelRegionOption.TOP_LEFT -> "Top Left"
                KioskControlPanelRegionOption.TOP_RIGHT -> "Top Right"
                KioskControlPanelRegionOption.BOTTOM_LEFT -> "Bottom Left"
                KioskControlPanelRegionOption.BOTTOM_RIGHT -> "Bottom Right"
                KioskControlPanelRegionOption.TOP -> "Top"
                KioskControlPanelRegionOption.BOTTOM -> "Bottom"
                KioskControlPanelRegionOption.FULL -> "Full Screen"
                KioskControlPanelRegionOption.DISABLED -> "Disabled"
            }
        }
    )
}
