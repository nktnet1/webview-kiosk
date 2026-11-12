package uk.nktnet.webviewkiosk.ui.components.setting.fielditems.webbrowsing

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.UserSettingsKeys
import uk.nktnet.webviewkiosk.config.option.WebviewControlActionOption
import uk.nktnet.webviewkiosk.ui.components.setting.fields.EnumListSettingFieldItem
import uk.nktnet.webviewkiosk.ui.resources.displayLabelRes

@Composable
fun KioskControlPanelActionsSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }
    val settingKey = UserSettingsKeys.WebBrowsing.KIOSK_CONTROL_PANEL_ACTIONS

    EnumListSettingFieldItem(
        entries = WebviewControlActionOption.entries,
        getLabel = { stringResource(it.displayLabelRes) },
        getDefault = {
            WebviewControlActionOption.getDefaultKioskControlPanelOptions()
        },
        initialValue = userSettings.kioskControlPanelActions,
        settingKey = settingKey,
        restricted = userSettings.isRestricted(settingKey),
        onSave = { newList -> userSettings.kioskControlPanelActions = newList },
    )
}
