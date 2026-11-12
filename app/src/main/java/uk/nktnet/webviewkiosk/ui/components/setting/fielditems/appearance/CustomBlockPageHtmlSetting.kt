package uk.nktnet.webviewkiosk.ui.components.setting.fielditems.appearance

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import uk.nktnet.webviewkiosk.R
import uk.nktnet.webviewkiosk.config.Constants
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.UserSettingsKeys
import uk.nktnet.webviewkiosk.ui.components.setting.fields.TextSettingFieldItem

@Composable
fun CustomBlockPageHtmlSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }
    val settingKey = UserSettingsKeys.Appearance.CUSTOM_BLOCK_PAGE_HTML

    TextSettingFieldItem(
        placeholder =
            stringResource(
                R.string.ui_custom_block_page_html_example,
                Constants.WEBSITE_URL,
            ),
        initialValue = userSettings.customBlockPageHtml,
        settingKey = settingKey,
        restricted = userSettings.isRestricted(settingKey),
        isMultiline = true,
        onSave = { userSettings.customBlockPageHtml = it },
    )
}
