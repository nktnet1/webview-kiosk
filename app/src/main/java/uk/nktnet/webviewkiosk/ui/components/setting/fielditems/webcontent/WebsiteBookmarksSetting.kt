package uk.nktnet.webviewkiosk.ui.components.setting.fielditems.webcontent

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import uk.nktnet.webviewkiosk.R
import uk.nktnet.webviewkiosk.config.Constants
import uk.nktnet.webviewkiosk.config.SystemSettings
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.UserSettingsKeys
import uk.nktnet.webviewkiosk.ui.components.setting.fields.TextSettingFieldItem
import uk.nktnet.webviewkiosk.ui.components.webview.HistoryDialog
import uk.nktnet.webviewkiosk.utils.validateUrl

@Composable
fun WebsiteBookmarksSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }
    val systemSettings = remember { SystemSettings(context) }
    val settingKey = UserSettingsKeys.WebContent.WEBSITE_BOOKMARKS
    val restricted = userSettings.isRestricted(settingKey)

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
        restricted = restricted,
        isMultiline = true,
        validator = { input ->
            input.isEmpty() || input.lines().all { validateUrl(it.trim()) }
        },
        validationMessage = "Some lines contain invalid URLs",
        onSave = { input ->
            userSettings.websiteBookmarks = input
        },
        extraContent = { draftValue, setValue ->
            if (restricted) {
                return@TextSettingFieldItem
            }

            var isOpenHistoryDialog by remember { mutableStateOf(false) }
            val currentUrl = systemSettings.currentUrl
            fun appendBookmark(newUrl: String) {
                setValue(
                    if (draftValue.isBlank()) {
                        newUrl
                    } else {
                        "$draftValue\n$newUrl"
                    }
                )
            }

            Button(
                onClick = { appendBookmark(currentUrl) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                shape = RoundedCornerShape(4.dp),
                colors = ButtonDefaults.buttonColors()
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Append the current URL:",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = currentUrl,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            Button(
                onClick = { isOpenHistoryDialog = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                shape = RoundedCornerShape(4.dp),
                colors = ButtonDefaults.buttonColors()
            ) {
                Text(
                    text = "Select from History",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            HistoryDialog(
                isOpenHistoryDialog,
                { isOpenHistoryDialog = false },
                { item, _ ->
                    appendBookmark(item.url)
                },
                false,
            )
        }
    )
}
