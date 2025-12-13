package com.nktnet.webview_kiosk.ui.components.setting.fielditems.webbrowsing

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.nktnet.webview_kiosk.R
import com.nktnet.webview_kiosk.config.UserSettings
import com.nktnet.webview_kiosk.config.UserSettingsKeys
import com.nktnet.webview_kiosk.config.option.KioskControlPanelActionOption
import com.nktnet.webview_kiosk.ui.components.setting.fields.EnumListSettingFieldItem

@Composable
fun KioskControlPanelActionsSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }
    val settingKey = UserSettingsKeys.WebBrowsing.KIOSK_CONTROL_PANEL_ACTIONS

    EnumListSettingFieldItem(
        label = stringResource(id = R.string.web_browsing_kiosk_control_panel_actions_title),
        infoText = """
            Manage visible actions in the kiosk control panel.

            Use the drag handle at the end to reorder the items.

            When in locked mode, the "UNLOCK" action will be appended to the
            end if not configured.

            When in unlocked mode, if "Appearance -> Floating Toolbar Mode" is
            set to Hidden, the "SETTINGS" action will be appended to the end
            if not configured.
        """.trimIndent(),
        entries = KioskControlPanelActionOption.entries,
        getLabel = { it.label },
        getDefault = { KioskControlPanelActionOption.getDefault() },
        initialValue = userSettings.kioskControlPanelActions,
        settingKey = settingKey,
        restricted = userSettings.isRestricted(settingKey),

        onSave = { newList -> userSettings.kioskControlPanelActions = newList }
    )
}
