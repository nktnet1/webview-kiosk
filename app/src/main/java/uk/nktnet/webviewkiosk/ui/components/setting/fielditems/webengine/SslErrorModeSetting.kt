package uk.nktnet.webviewkiosk.ui.components.setting.fielditems.webengine

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.UserSettingsKeys
import uk.nktnet.webviewkiosk.config.option.SslErrorModeOption
import uk.nktnet.webviewkiosk.ui.components.setting.fields.DropdownSettingFieldItem

@Composable
fun SslErrorModeSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }

    DropdownSettingFieldItem(
        label = "SSL Error Mode",
        infoText = """
            Controls how the WebView handles SSL errors when loading pages.

            - BLOCK: cancels all failed SSL requests (default).  
            - PROMPT: opens a dialog for the user to decide.
            - PROCEED: always proceeds despite SSL errors (dangerous, NOT RECOMMENDED).

            Please use this setting carefully. Proceeding on SSL errors can compromise security.
        """.trimIndent(),
        options = SslErrorModeOption.entries,
        initialValue = userSettings.sslErrorMode,
        restricted = userSettings.isRestricted(UserSettingsKeys.WebEngine.SSL_ERROR_MODE),
        onSave = { userSettings.sslErrorMode = it },
        itemText = { it.label },
    )
}
