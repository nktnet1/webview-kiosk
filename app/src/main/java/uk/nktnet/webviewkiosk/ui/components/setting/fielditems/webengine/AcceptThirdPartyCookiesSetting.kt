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
fun AcceptThirdPartyCookiesSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }
    val settingKey = UserSettingsKeys.WebEngine.ACCEPT_THIRD_PARTY_COOKIES

    BooleanSettingFieldItem(
        label = stringResource(R.string.web_engine_accept_third_party_cookies_title),
        infoText = stringResource(R.string.setting_info_accept_third_party_cookies),
        initialValue = userSettings.acceptThirdPartyCookies,
        settingKey = settingKey,
        restricted = userSettings.isRestricted(settingKey),
        onSave = { userSettings.acceptThirdPartyCookies = it },
    )
}
