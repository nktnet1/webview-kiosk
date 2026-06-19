package uk.nktnet.webviewkiosk.ui.components.setting.fielditems.jsscript

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import uk.nktnet.webviewkiosk.R
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.UserSettingsKeys
import uk.nktnet.webviewkiosk.ui.components.setting.fields.BooleanSettingFieldItem

@Composable
fun EnableDarkReaderSetting() {
    val context = LocalContext.current
    val userSettings = UserSettings(context)
    val settingKey = UserSettingsKeys.JsScripts.ENABLE_DARK_READER

    BooleanSettingFieldItem(
        label = stringResource(R.string.js_scripts_enable_dark_reader_title),
        infoText = """
            Automatically inject Dark Reader into web pages.

            - https://github.com/darkreader/darkreader

            This applies dynamic CSS transformations to force dark mode on websites
            that do not support it natively.

            Note: Some websites may render incorrectly due to CSS overrides.
        """.trimIndent(),
        initialValue = userSettings.enableDarkReader,
        settingKey = settingKey,
        restricted = userSettings.isRestricted(settingKey),
        onSave = { userSettings.enableDarkReader = it }
    )
}
