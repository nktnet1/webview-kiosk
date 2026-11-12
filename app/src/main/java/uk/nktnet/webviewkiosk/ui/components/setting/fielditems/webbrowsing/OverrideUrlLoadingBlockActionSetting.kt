package uk.nktnet.webviewkiosk.ui.components.setting.fielditems.webbrowsing

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.UserSettingsKeys
import uk.nktnet.webviewkiosk.config.option.OverrideUrlLoadingBlockActionOption
import uk.nktnet.webviewkiosk.ui.components.setting.fields.DropdownSettingFieldItem
import uk.nktnet.webviewkiosk.ui.resources.displayLabelRes

@Composable
fun OverrideUrlLoadingBlockActionSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }
    val settingKey = UserSettingsKeys.WebBrowsing.OVERRIDE_URL_LOADING_BLOCK_ACTION

    DropdownSettingFieldItem(
        options = OverrideUrlLoadingBlockActionOption.entries,
        settingKey = settingKey,
        restricted = userSettings.isRestricted(settingKey),
        initialValue = userSettings.overrideUrlLoadingBlockAction,
        onSave = { userSettings.overrideUrlLoadingBlockAction = it },
        itemText = { stringResource(it.displayLabelRes) },
    )
}
