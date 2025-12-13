package com.nktnet.webview_kiosk.ui.components.setting.fielditems.weblifecycle

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.nktnet.webview_kiosk.R
import com.nktnet.webview_kiosk.config.Constants
import com.nktnet.webview_kiosk.config.UserSettings
import com.nktnet.webview_kiosk.config.UserSettingsKeys
import com.nktnet.webview_kiosk.ui.components.setting.fields.NumberSettingFieldItem

@Composable
fun RefreshOnLoadingErrorIntervalSecondsSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }
    val settingKey = UserSettingsKeys.WebLifecycle.REFRESH_ON_LOADING_ERROR_INTERVAL_SECONDS

    NumberSettingFieldItem(
        label = stringResource(id = R.string.web_lifecycle_refresh_on_loading_error_interval_seconds_title),
        infoText = """
            Interval in seconds between automatic refresh attempts when a page fails
            to load. This usually indicate the inability to connect to the server,
            e.g. due to no internet connection or the server is offline.

            Minimum: ${Constants.MIN_REFRESH_ON_LOADING_ERROR_INTERVAL_SECONDS}

            To disable, use the value 0.
        """.trimIndent(),
        placeholder = "e.g. 5",
        initialValue = userSettings.refreshOnLoadingErrorIntervalSeconds,
        settingKey = settingKey,
        restricted = userSettings.isRestricted(settingKey),
        min = Constants.MIN_REFRESH_ON_LOADING_ERROR_INTERVAL_SECONDS,
        onSave = { userSettings.refreshOnLoadingErrorIntervalSeconds = it }
    )
}
