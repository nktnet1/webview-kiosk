package com.nktnet.webview_kiosk.utils

import com.nktnet.webview_kiosk.config.UserSettings
import com.nktnet.webview_kiosk.config.option.BackButtonHoldActionOption
import com.nktnet.webview_kiosk.config.option.FloatingToolbarModeOption

fun canDisableKioskControlPanelRegion(userSettings: UserSettings): Boolean {
    return !(
        userSettings.floatingToolbarMode == FloatingToolbarModeOption.HIDDEN
        && userSettings.backButtonHoldAction != BackButtonHoldActionOption.OPEN_KIOSK_CONTROL_PANEL
    )
}
