package uk.nktnet.webviewkiosk.ui.components.setting.fielditems.webengine

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import uk.nktnet.webviewkiosk.R
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.UserSettingsKeys
import uk.nktnet.webviewkiosk.ui.components.setting.fields.BooleanSettingFieldItem

@Composable
fun RequestFocusOnPageStartSetting() {
    val context = LocalContext.current
    val userSettings = UserSettings(context)
    val settingKey = UserSettingsKeys.WebEngine.REQUEST_FOCUS_ON_PAGE_START

    BooleanSettingFieldItem(
        label = stringResource(R.string.web_engine_request_focus_on_page_start_title),
        infoText = """
            Sets whether the WebView should request focus when a page starts loading.
        """.trimIndent(),
        initialValue = userSettings.requestFocusOnPageStart,
        settingKey = settingKey,
        restricted = userSettings.isRestricted(settingKey),
        onSave = { userSettings.requestFocusOnPageStart = it }
    )
}
