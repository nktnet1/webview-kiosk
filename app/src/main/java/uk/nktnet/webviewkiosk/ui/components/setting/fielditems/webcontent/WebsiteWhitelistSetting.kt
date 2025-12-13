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
fun WebsiteWhitelistSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }
    val settingKey = UserSettingsKeys.WebContent.WEBSITE_WHITELIST

    TextSettingFieldItem(
        label = stringResource(id = R.string.web_content_website_whitelist_title),
        infoText = """
            Specify regular expressions (regex), one per line.

            Escaping with backslash (\) is required for special characters
            in regex like '.' and '?'.

            These patterns also use partial matching.
            If you need strict control, anchor your regex with `^` and `$`.

            Whitelist patterns take precedence over blacklist patterns.
        """.trimIndent(),
        placeholder = """
            e.g.
                ^https://trusted\.org/?$
                ^https://.*\.trusted\.org/.*
        """.trimIndent(),
        initialValue = userSettings.websiteWhitelist,
        settingKey = settingKey,
        restricted = userSettings.isRestricted(settingKey),
        isMultiline = true,
        validator = { validateMultilineRegex(it) },
        validationMessage = "Some lines contain invalid regular expressions.",
        onSave = { userSettings.websiteWhitelist = it }
    )
}
