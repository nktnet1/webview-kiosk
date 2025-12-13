package com.nktnet.webview_kiosk.ui.components.setting.fielditems.webbrowsing

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.nktnet.webview_kiosk.R
import com.nktnet.webview_kiosk.config.UserSettings
import com.nktnet.webview_kiosk.config.UserSettingsKeys
import com.nktnet.webview_kiosk.config.option.KioskControlPanelRegionOption
import com.nktnet.webview_kiosk.ui.components.setting.fields.DropdownSettingFieldItem
import com.nktnet.webview_kiosk.utils.canDisableKioskControlPanelRegion

@Composable
fun KioskControlPanelRegionSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }
    val settingKey = UserSettingsKeys.WebBrowsing.KIOSK_CONTROL_PANEL_REGION

    DropdownSettingFieldItem(
        label = stringResource(id = R.string.web_browsing_kiosk_control_panel_region_title),
        infoText = """
            Controls which part of the screen can be multi-tapped 10 times in quick
            succession to show the Kiosk Control Panel.

            The default is Top Left (upper-left quadrant of the screen).

            If both of the following are true:
              1. [Web Browsing -> Kiosk Control Panel Region] is disabled
              2. [Device -> Back Button Hold Action] is not set to "Open Kiosk Control Panel"
            Then this option cannot be disabled, and will default to Top Left.
        """.trimIndent(),
        options = KioskControlPanelRegionOption.entries,
        initialValue = userSettings.kioskControlPanelRegion,
        settingKey = settingKey,
        restricted = userSettings.isRestricted(settingKey),
        onSave = { userSettings.kioskControlPanelRegion = it },
        validator = {
            it != KioskControlPanelRegionOption.DISABLED
            || canDisableKioskControlPanelRegion(userSettings)
        },
        validationMessage = """
            You cannot disable this option because:
              1. [Web Browsing -> Kiosk Control Panel Region] is disabled
              2. [Device -> Back Button Hold Action] is not set to "Open Kiosk Control Panel"
            """.trimIndent(),
        itemText = {
            if (
                it == KioskControlPanelRegionOption.DISABLED
                && !canDisableKioskControlPanelRegion(userSettings)
            ) {
                "Top Left (cannot disable)"
            } else {
                it.label
            }
        }
    )
}
