package uk.nktnet.webviewkiosk.ui.components.setting.fielditems.webengine

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import uk.nktnet.webviewkiosk.R
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.UserSettingsKeys
import uk.nktnet.webviewkiosk.ui.components.setting.fields.BooleanSettingFieldItem

@Composable
fun AllowFileAccessFromFileURLsSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }
    val settingKey = UserSettingsKeys.WebEngine.ALLOW_FILE_ACCESS_FROM_FILE_URLS

    BooleanSettingFieldItem(
        label =
            stringResource(
                R.string.web_engine_allow_file_access_from_file_urls_title
            ),
        infoText =
            stringResource(R.string.setting_info_allow_file_access_from_file_url),
        initialValue = userSettings.allowFileAccessFromFileURLs,
        settingKey = settingKey,
        restricted = userSettings.isRestricted(settingKey),
        onSave = { userSettings.allowFileAccessFromFileURLs = it },
    )
}
