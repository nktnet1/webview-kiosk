package uk.nktnet.webviewkiosk.ui.components.setting.fielditems.webengine

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import uk.nktnet.webviewkiosk.R
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.UserSettingsKeys
import uk.nktnet.webviewkiosk.ui.components.setting.fields.BooleanSettingFieldItem
import uk.nktnet.webviewkiosk.utils.getDownloadLocation

@Composable
fun AllowFileDownloadSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }
    val settingKey = UserSettingsKeys.WebEngine.ALLOW_FILE_DOWNLOAD

    val downloadLocation = remember { getDownloadLocation() }

    BooleanSettingFieldItem(
        label = stringResource(R.string.web_engine_allow_file_download_title),
        infoText = """
            Allow files to be downloaded from websites to:

            - $downloadLocation

            For Android 9.0 (SDK 28) and below, the WRITE_EXTERNAL_STORAGE
            permission is required.
        """.trimIndent(),
        initialValue = userSettings.allowFileDownload,
        settingKey = settingKey,
        restricted = userSettings.isRestricted(settingKey),
        onSave = { userSettings.allowFileDownload = it }
    )
}
