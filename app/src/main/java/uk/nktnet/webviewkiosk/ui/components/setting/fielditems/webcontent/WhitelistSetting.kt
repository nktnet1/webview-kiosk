package uk.nktnet.webviewkiosk.ui.components.setting.fielditems.webcontent

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.UserSettingsKeys
import uk.nktnet.webviewkiosk.ui.components.setting.fields.TextSettingFieldItem
import uk.nktnet.webviewkiosk.utils.validateMultilineRegex

@Composable
fun WhitelistSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }

    TextSettingFieldItem(
        label = "Whitelist Regex",
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
        restricted = userSettings.isRestricted(UserSettingsKeys.WebContent.WEBSITE_WHITELIST),
        isMultiline = true,
        validator = { validateMultilineRegex(it) },
        validationMessage = "Some lines contain invalid regular expressions.",
        onSave = { userSettings.websiteWhitelist = it }
    )
}
