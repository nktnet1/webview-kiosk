package uk.nktnet.webviewkiosk.ui.components.setting.fielditems.webcontent

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import uk.nktnet.webviewkiosk.R
import uk.nktnet.webviewkiosk.config.Constants
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.UserSettingsKeys
import uk.nktnet.webviewkiosk.ui.components.setting.fields.TextSettingFieldItem
import uk.nktnet.webviewkiosk.utils.validateUrl

@Composable
fun WebsiteBookmarksSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }
    val settingKey = UserSettingsKeys.WebContent.WEBSITE_BOOKMARKS

    TextSettingFieldItem(
        label = stringResource(R.string.web_content_website_bookmarks_title ),
        infoText = """
            Add your bookmark URLs, one per line.
            You can access the bookmarks using the address bar menu.
        """.trimIndent(),
        placeholder = """
            e.g.
            ${Constants.WEBSITE_URL}
            ${Constants.SOURCE_CODE_URL}
        """.trimIndent(),
        initialValue = userSettings.websiteBookmarks,
        settingKey = settingKey,
        restricted = userSettings.isRestricted(settingKey),
        isMultiline = true,
        validator = { input ->
            input.isEmpty() || input.lines().all { validateUrl(it.trim()) }
        },
        validationMessage = "Some lines contain invalid URLs",
        onSave = { input ->
            userSettings.websiteBookmarks = input
        }
    )
}
