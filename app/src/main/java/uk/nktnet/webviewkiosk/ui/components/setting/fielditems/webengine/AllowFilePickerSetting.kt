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
fun AllowFilePickerSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }
    val settingKey = UserSettingsKeys.WebEngine.ALLOW_FILE_PICKER

    BooleanSettingFieldItem(
        label = stringResource(R.string.web_engine_allow_file_picker_title),
        infoText = """
            Allow websites to open the system file picker, e.g. for uploading files.
        """.trimIndent(),
        initialValue = userSettings.allowFilePicker,
        settingKey = settingKey,
        restricted = userSettings.isRestricted(settingKey),
        onSave = { userSettings.allowFilePicker = it }
    )
}
