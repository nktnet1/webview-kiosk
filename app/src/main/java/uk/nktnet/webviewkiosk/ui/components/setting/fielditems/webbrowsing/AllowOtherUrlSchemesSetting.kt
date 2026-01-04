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
fun AllowOtherUrlSchemesSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }
    val settingKey = UserSettingsKeys.WebBrowsing.ALLOW_OTHER_URL_SCHEMES

    BooleanSettingFieldItem(
        label = stringResource(R.string.web_browsing_allow_other_url_schemes_title),
        infoText = """
            Allow the handling of non-http/https URL schemes (i.e. intents)
            such as intent:, mailto:, sms:, tel:, spotify:, whatsapp:, unifiedpush:,
            etc in other apps.

            When in Lock Task Mode, the apps responsible for handling these
            intents needs to be present in the Lock Task Permitted list under
            the device owner settings to function.
        """.trimIndent(),
        initialValue = userSettings.allowOtherUrlSchemes,
        settingKey = settingKey,
        restricted = userSettings.isRestricted(settingKey),
        onSave = { userSettings.allowOtherUrlSchemes = it }
    )
}
