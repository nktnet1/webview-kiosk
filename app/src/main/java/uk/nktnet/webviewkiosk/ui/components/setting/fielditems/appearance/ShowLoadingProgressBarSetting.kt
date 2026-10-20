package uk.nktnet.webviewkiosk.ui.components.setting.fielditems.appearance

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import uk.nktnet.webviewkiosk.R
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.UserSettingsKeys
import uk.nktnet.webviewkiosk.ui.components.setting.fields.BooleanSettingFieldItem

@Composable
fun ShowLoadingProgressBarSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }
    val settingKey = UserSettingsKeys.Appearance.SHOW_LOADING_PROGRESS_BAR

    BooleanSettingFieldItem(
        label = stringResource(R.string.appearance_show_loading_progress_bar_title),
        infoText =
            """
            Show a progress bar at the top of the WebView while content is loading.

            Disable this for signage pages where background iframe loads make
            the bar appear even when the visible content is unchanged.

            This only controls the bar's visibility. Page loading is unaffected.
            """
                .trimIndent(),
        settingKey = settingKey,
        restricted = userSettings.isRestricted(settingKey),
        initialValue = userSettings.showLoadingProgressBar,
        onSave = { userSettings.showLoadingProgressBar = it },
    )
}
