package uk.nktnet.webviewkiosk.utils

import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.option.BackButtonHoldActionOption
import uk.nktnet.webviewkiosk.config.option.FloatingToolbarModeOption

fun canDisableKioskControlPanelRegion(userSettings: UserSettings): Boolean {
    return !(
        userSettings.floatingToolbarMode == FloatingToolbarModeOption.HIDDEN
        && userSettings.backButtonHoldAction != BackButtonHoldActionOption.OPEN_KIOSK_CONTROL_PANEL
    )
}
