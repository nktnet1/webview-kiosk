package uk.nktnet.webviewkiosk.ui.components.setting.fielditems.webbrowsing

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import uk.nktnet.webviewkiosk.R
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.UserSettingsKeys
import uk.nktnet.webviewkiosk.config.option.WebviewControlActionOption
import uk.nktnet.webviewkiosk.ui.components.setting.fields.EnumListSettingFieldItem

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
        entries = WebviewControlActionOption.entries,
        getLabel = { it.label },
        getDefault = { WebviewControlActionOption.getDefaultKioskControlPanelOptions() },
        initialValue = userSettings.kioskControlPanelActions,
        settingKey = settingKey,
        restricted = userSettings.isRestricted(settingKey),

        onSave = { newList -> userSettings.kioskControlPanelActions = newList }
    )
}
