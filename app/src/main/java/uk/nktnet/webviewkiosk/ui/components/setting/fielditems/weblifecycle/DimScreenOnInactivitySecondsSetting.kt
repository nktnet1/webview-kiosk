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
fun DimScreenOnInactivitySecondsSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }
    val settingKey = UserSettingsKeys.WebLifecycle.DIM_SCREEN_ON_INACTIVITY_SECONDS

    NumberSettingFieldItem(
        infoText =
            stringResource(
                settingDefinition(settingKey).descriptionRes,
                Constants.MIN_INACTIVITY_TIMEOUT_SECONDS,
            ),
        placeholder = stringResource(R.string.ui_e_g_120),
        initialValue = userSettings.dimScreenOnInactivitySeconds,
        settingKey = settingKey,
        restricted = userSettings.isRestricted(settingKey),
        min = Constants.MIN_INACTIVITY_TIMEOUT_SECONDS,
        max = Constants.MAX_INT_SETTING,
        onSave = { userSettings.dimScreenOnInactivitySeconds = it },
    )
}
