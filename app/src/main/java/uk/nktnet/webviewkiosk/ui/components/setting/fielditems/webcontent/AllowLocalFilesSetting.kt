package uk.nktnet.webviewkiosk.ui.components.setting.fielditems.webcontent

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import uk.nktnet.webviewkiosk.R
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.UserSettingsKeys
import uk.nktnet.webviewkiosk.ui.components.setting.fields.BooleanSettingFieldItem

@Composable
fun AllowLocalFilesSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }
    val settingKey = UserSettingsKeys.WebContent.ALLOW_LOCAL_FILES

    BooleanSettingFieldItem(
        label = stringResource(R.string.web_content_allow_local_files_title),
        infoText = """
            Allow the WebView to open uploaded local files such as HTML, PDF, images,
            audio, video and text.

            Upload and manage files using "Web Content -> Manage Local Files".
            Open them with the Files action in the address bar menu or kiosk
            control panel when that action is enabled.
        """.trimIndent(),
        initialValue = userSettings.allowLocalFiles,
        settingKey = settingKey,
        restricted = userSettings.isRestricted(settingKey),
        onSave = { userSettings.allowLocalFiles = it }
    )
}
