package uk.nktnet.webviewkiosk.ui.components.setting.fielditems.webbrowsing

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import uk.nktnet.webviewkiosk.R
import uk.nktnet.webviewkiosk.config.Constants
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.UserSettingsKeys
import uk.nktnet.webviewkiosk.ui.components.setting.fields.TextSettingFieldItem
import uk.nktnet.webviewkiosk.utils.validateUrl

@Composable
fun SearchProviderUrlSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }
    val settingKey = UserSettingsKeys.WebBrowsing.SEARCH_PROVIDER_URL
    val restricted = userSettings.isRestricted(settingKey)

    val searchProviders =
        listOf(
            stringResource(R.string.ui_search_provider_google) to
                "https://google.com/search?q=",
            stringResource(R.string.ui_search_provider_duckduckgo) to
                "https://duckduckgo.com?q=",
            stringResource(R.string.ui_search_provider_bing) to
                "https://bing.com/search?q=",
            stringResource(R.string.ui_search_provider_yahoo) to
                "https://search.yahoo.com/search?p=",
            stringResource(R.string.ui_search_provider_start_page) to
                "https://startpage.com/do/search?q=",
            stringResource(R.string.ui_search_provider_ecosia) to
                "https://ecosia.org/search?q=",
            stringResource(R.string.ui_search_provider_unduck) to
                "https://unduck.link?q=",
            stringResource(R.string.ui_search_provider_unduckified) to
                "https://s.dunkirk.sh?q=",
        )

    TextSettingFieldItem(
        label = stringResource(R.string.web_browsing_search_provider_url_title),
        infoText =
            stringResource(
                R.string.setting_info_search_provider_url,
                Constants.DEFAULT_SEARCH_PROVIDER_URL,
            ),
        placeholder = Constants.DEFAULT_SEARCH_PROVIDER_URL,
        initialValue = userSettings.searchProviderUrl,
        settingKey = settingKey,
        restricted = userSettings.isRestricted(settingKey),
        isMultiline = false,
        validator = { validateUrl(it) },
        validationMessage = stringResource(R.string.ui_invalid_search_provider_url),
        onSave = { userSettings.searchProviderUrl = it },
        extraContent = { _, setValue ->
            if (restricted) {
                return@TextSettingFieldItem
            }

            var lastHighlight by remember {
                mutableStateOf(userSettings.searchProviderUrl)
            }

            Column(
                modifier =
                    Modifier.fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(top = 8.dp)
            ) {
                searchProviders.chunked(2).forEach { rowProviders ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    ) {
                        rowProviders.forEach { (name, url) ->
                            val isSelected = lastHighlight == url
                            Button(
                                onClick = {
                                    lastHighlight = url
                                    setValue(url)
                                },
                                modifier =
                                    Modifier.weight(1f)
                                        .border(
                                            1.dp,
                                            if (isSelected) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                MaterialTheme.colorScheme
                                                    .outlineVariant
                                            },
                                            RoundedCornerShape(4.dp),
                                        ),
                                shape = RoundedCornerShape(4.dp),
                                colors =
                                    ButtonDefaults.buttonColors(
                                        containerColor = Color.Transparent,
                                        contentColor =
                                            if (isSelected) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                MaterialTheme.colorScheme.onSurface
                                            },
                                    ),
                                elevation =
                                    ButtonDefaults.buttonElevation(
                                        defaultElevation = 0.dp
                                    ),
                            ) {
                                Text(
                                    text = name,
                                    style = MaterialTheme.typography.bodySmall,
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                        if (rowProviders.size < 2) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        },
    )
}
