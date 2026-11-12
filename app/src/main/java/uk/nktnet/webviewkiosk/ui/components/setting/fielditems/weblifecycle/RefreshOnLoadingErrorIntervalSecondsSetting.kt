package uk.nktnet.webviewkiosk.ui.components.setting.fielditems.weblifecycle

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import uk.nktnet.webviewkiosk.R
import uk.nktnet.webviewkiosk.config.Constants
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.UserSettingsKeys
import uk.nktnet.webviewkiosk.ui.components.setting.fields.NumberSettingFieldItem
import uk.nktnet.webviewkiosk.ui.screens.settingDefinition

@Composable
fun RefreshOnLoadingErrorIntervalSecondsSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }
    val settingKey =
        UserSettingsKeys.WebLifecycle.REFRESH_ON_LOADING_ERROR_INTERVAL_SECONDS

    NumberSettingFieldItem(
        infoText =
            stringResource(
                settingDefinition(settingKey).descriptionRes,
                Constants.MIN_REFRESH_ON_LOADING_ERROR_INTERVAL_SECONDS,
            ),
        placeholder = stringResource(R.string.ui_e_g_5),
        initialValue = userSettings.refreshOnLoadingErrorIntervalSeconds,
        settingKey = settingKey,
        restricted = userSettings.isRestricted(settingKey),
        min = Constants.MIN_REFRESH_ON_LOADING_ERROR_INTERVAL_SECONDS,
        max = Constants.MAX_INT_SETTING,
        onSave = { userSettings.refreshOnLoadingErrorIntervalSeconds = it },
    )
}
