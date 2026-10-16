package uk.nktnet.webviewkiosk.ui.components.setting.fielditems.webbrowsing

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import uk.nktnet.webviewkiosk.R
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.UserSettingsKeys
import uk.nktnet.webviewkiosk.config.option.KioskControlPanelRegionOption
import uk.nktnet.webviewkiosk.ui.components.setting.fields.DropdownSettingFieldItem
import uk.nktnet.webviewkiosk.utils.canDisableKioskControlPanelRegion

@Composable
fun KioskControlPanelRegionSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }
    val settingKey = UserSettingsKeys.WebBrowsing.KIOSK_CONTROL_PANEL_REGION

    DropdownSettingFieldItem(
        label =
            stringResource(R.string.web_browsing_kiosk_control_panel_region_title),
        infoText =
            """
            Controls which part of the screen can be multi-tapped 10 times in quick
            succession to show the Kiosk Control Panel.

            The default is Top Left (upper-left quadrant of the screen).

            You cannot disable the multi-tap region when both of these apply:
              1. [Appearance -> Floating Toolbar Mode] is set to Hidden
              2. [Device -> Back Button Hold Action] is not set to "Open Kiosk Control Panel"
            In that case, the region stays at Top Left so the panel remains accessible.
            """
                .trimIndent(),
        options = KioskControlPanelRegionOption.entries,
        initialValue = userSettings.kioskControlPanelRegion,
        settingKey = settingKey,
        restricted = userSettings.isRestricted(settingKey),
        onSave = { userSettings.kioskControlPanelRegion = it },
        validator = {
            it != KioskControlPanelRegionOption.DISABLED ||
                canDisableKioskControlPanelRegion(userSettings)
        },
        validationMessage =
            """
            The Kiosk Control Panel needs a way to open. You cannot disable
            this region while the floating toolbar is Hidden and Back Button
            Hold Action is not set to "Open Kiosk Control Panel".
            """
                .trimIndent(),
        itemText = {
            if (
                it == KioskControlPanelRegionOption.DISABLED &&
                    !canDisableKioskControlPanelRegion(userSettings)
            ) {
                "Top Left (cannot disable)"
            } else {
                it.label
            }
        },
    )
}
