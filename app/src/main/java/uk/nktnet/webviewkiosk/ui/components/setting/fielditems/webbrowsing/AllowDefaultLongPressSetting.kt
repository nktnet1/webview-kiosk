package uk.nktnet.webviewkiosk.ui.components.setting.fielditems.webbrowsing

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import uk.nktnet.webviewkiosk.R
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.UserSettingsKeys
import uk.nktnet.webviewkiosk.ui.components.setting.fields.BooleanSettingFieldItem

@Composable
fun AllowDefaultLongPressSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }
    val settingKey = UserSettingsKeys.WebBrowsing.ALLOW_DEFAULT_LONG_PRESS

    BooleanSettingFieldItem(
        label = stringResource(id = R.string.web_browsing_allow_default_long_press_title),
        infoText = """
            When enabled, long-pressing areas in the WebView will trigger the native
            WebView behaviour, e.g. text selection.

            Specifically for links, even if this is set to false, it can be overridden
            by the "Allow Link Long Press Context Menu" setting.
        """.trimIndent(),
        initialValue = userSettings.allowDefaultLongPress,
        settingKey = settingKey,
        restricted = userSettings.isRestricted(settingKey),
        onSave = { userSettings.allowDefaultLongPress = it }
    )
}
