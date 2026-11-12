package uk.nktnet.webviewkiosk.ui.components.setting.fielditems.weblifecycle

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.UserSettingsKeys
import uk.nktnet.webviewkiosk.config.option.RefreshOnNetworkAvailableOption
import uk.nktnet.webviewkiosk.ui.components.setting.fields.DropdownSettingFieldItem
import uk.nktnet.webviewkiosk.ui.resources.displayLabelRes

@Composable
fun RefreshOnNetworkAvailableSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }
    val settingKey = UserSettingsKeys.WebLifecycle.REFRESH_ON_NETWORK_AVAILABLE

    DropdownSettingFieldItem(
        options = RefreshOnNetworkAvailableOption.entries,
        initialValue = userSettings.refreshOnNetworkAvailable,
        settingKey = settingKey,
        restricted = userSettings.isRestricted(settingKey),
        itemText = { stringResource(it.displayLabelRes) },
        onSave = {
            userSettings.refreshOnNetworkAvailable = it
        },
    )
}
