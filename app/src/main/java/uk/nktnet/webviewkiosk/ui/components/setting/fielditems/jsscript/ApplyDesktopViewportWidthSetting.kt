package uk.nktnet.webviewkiosk.ui.components.setting.fielditems.jsscript

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
fun ApplyDesktopViewportWidthSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }
    val settingKey = UserSettingsKeys.JsScripts.APPLY_DESKTOP_VIEWPORT_WIDTH

    NumberSettingFieldItem(
        infoText =
            stringResource(
                settingDefinition(settingKey).descriptionRes,
                Constants.MIN_DESKTOP_WIDTH,
            ),
        initialValue = userSettings.applyDesktopViewportWidth,
        settingKey = settingKey,
        restricted = userSettings.isRestricted(settingKey),
        min = Constants.MIN_DESKTOP_WIDTH,
        max = Constants.MAX_INT_SETTING,
        placeholder = stringResource(R.string.ui_e_g_1024_or_0_to_disable),
        onSave = { userSettings.applyDesktopViewportWidth = it },
    )
}
