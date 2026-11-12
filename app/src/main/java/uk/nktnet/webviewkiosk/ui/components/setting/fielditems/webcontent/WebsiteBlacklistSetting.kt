package uk.nktnet.webviewkiosk.ui.components.setting.fielditems.webcontent

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import uk.nktnet.webviewkiosk.R
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.UserSettingsKeys
import uk.nktnet.webviewkiosk.ui.components.setting.fields.TextSettingFieldItem
import uk.nktnet.webviewkiosk.utils.validateMultilineRegex

@Composable
fun WebsiteBlacklistSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }
    val settingKey = UserSettingsKeys.WebContent.WEBSITE_BLACKLIST

    TextSettingFieldItem(
        placeholder = stringResource(R.string.ui_website_blacklist_example),
        initialValue = userSettings.websiteBlacklist,
        settingKey = settingKey,
        restricted = userSettings.isRestricted(settingKey),
        isMultiline = true,
        validator = { validateMultilineRegex(it) },
        validationMessage =
            stringResource(
                R.string.ui_some_lines_contain_invalid_regular_expressions
            ),
        onSave = { userSettings.websiteBlacklist = it },
    )
}
