package uk.nktnet.webviewkiosk.ui.components.setting.fielditems.weblifecycle

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import uk.nktnet.webviewkiosk.R
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.UserSettingsKeys
import uk.nktnet.webviewkiosk.config.option.RefreshOnNetworkAvailableOption
import uk.nktnet.webviewkiosk.ui.components.setting.fields.DropdownSettingFieldItem

@Composable
fun RefreshOnNetworkAvailableSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }
    val settingKey = UserSettingsKeys.WebLifecycle.REFRESH_ON_NETWORK_AVAILABLE

    DropdownSettingFieldItem(
        label = stringResource(R.string.web_lifecycle_refresh_on_network_available_title),
        infoText = """
            Select when the page should automatically refresh when a
            network connection becomes available.

            - Always: refresh whenever a network connection is available
            - On Page Error: only refresh if the previous page load failed
            - Never: do not refresh automatically
        """.trimIndent(),
        options = RefreshOnNetworkAvailableOption.entries,
        initialValue = userSettings.refreshOnNetworkAvailable,
        settingKey = settingKey,
        restricted = userSettings.isRestricted(settingKey),
        itemText = { it.label },
        onSave = {
            userSettings.refreshOnNetworkAvailable = it
        },
    )
}
