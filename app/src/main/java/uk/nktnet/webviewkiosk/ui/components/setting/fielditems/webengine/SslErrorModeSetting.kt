package com.nktnet.webview_kiosk.ui.components.setting.fielditems.webengine

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.nktnet.webview_kiosk.R
import com.nktnet.webview_kiosk.config.UserSettings
import com.nktnet.webview_kiosk.config.UserSettingsKeys
import com.nktnet.webview_kiosk.config.option.SslErrorModeOption
import com.nktnet.webview_kiosk.ui.components.setting.fields.DropdownSettingFieldItem

@Composable
fun SslErrorModeSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }
    val settingKey = UserSettingsKeys.WebEngine.SSL_ERROR_MODE

    DropdownSettingFieldItem(
        label = stringResource(id = R.string.web_engine_ssl_error_mode_title),
        infoText = """
            Controls how the WebView handles SSL errors when loading pages.

            - BLOCK: cancels all failed SSL requests (default).
            - PROMPT: opens a dialog for the user to decide.
            - PROCEED: always proceeds despite SSL errors (dangerous, NOT RECOMMENDED).

            Please use this setting carefully. Proceeding on SSL errors can compromise security.
        """.trimIndent(),
        options = SslErrorModeOption.entries,
        initialValue = userSettings.sslErrorMode,
        settingKey = settingKey,
        restricted = userSettings.isRestricted(settingKey),
        onSave = { userSettings.sslErrorMode = it },
        itemText = { it.label },
    )
}
