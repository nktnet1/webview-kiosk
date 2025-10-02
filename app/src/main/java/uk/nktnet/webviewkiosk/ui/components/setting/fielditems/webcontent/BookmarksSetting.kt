package uk.nktnet.webviewkiosk.ui.components.setting.fielditems.webcontent

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import uk.nktnet.webviewkiosk.config.Constants
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.ui.components.setting.fields.TextSettingFieldItem
import uk.nktnet.webviewkiosk.utils.validateUrl

@Composable
fun BookmarksSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }

    var currentValue by remember { mutableStateOf(userSettings.websiteBookmarks) }

    TextSettingFieldItem(
        label = "Bookmarks",
        infoText = """
            Add your bookmark URLs, one per line.
            You can access the bookmarks using the address bar menu.
        """.trimIndent(),
        placeholder = """
            e.g.
            ${Constants.WEBSITE_URL}
            ${Constants.GITHUB_URL}
        """.trimIndent(),
        initialValue = currentValue,
        isMultiline = true,
        validator = { input ->
            input.isEmpty() || input.lines().all { validateUrl(it.trim()) }
        },
        validationMessage = "Some lines contain invalid URLs",
        onSave = { input ->
            currentValue = input
            userSettings.websiteBookmarks = input
        }
    )
}
