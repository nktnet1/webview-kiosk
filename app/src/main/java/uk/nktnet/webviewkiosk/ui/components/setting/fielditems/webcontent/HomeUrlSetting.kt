package com.nktnet.webview_kiosk.ui.components.setting.fielditems.webcontent

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.nktnet.webview_kiosk.R
import com.nktnet.webview_kiosk.config.Constants
import com.nktnet.webview_kiosk.config.SystemSettings
import com.nktnet.webview_kiosk.config.UserSettings
import com.nktnet.webview_kiosk.config.UserSettingsKeys
import com.nktnet.webview_kiosk.ui.components.setting.fields.TextSettingFieldItem
import com.nktnet.webview_kiosk.utils.validateUrl

@Composable
fun HomeUrlSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }
    val systemSettings = remember { SystemSettings(context) }
    val settingKey = UserSettingsKeys.WebContent.HOME_URL
    val restricted = userSettings.isRestricted(settingKey)

    TextSettingFieldItem(
        label = stringResource(id = R.string.web_content_home_url_title),
        infoText = """
            The URL that can be returned to by:
              1. tapping the screen 10 times in quick succession
              2. using the floating toolbar icon
              3. using the address bar menu

            This is also controlled by the "Allow Go Home" setting under web browsing.
        """.trimIndent(),
        placeholder = "e.g. ${Constants.WEBSITE_URL}",
        initialValue = userSettings.homeUrl,
        settingKey = settingKey,
        restricted = restricted,
        isMultiline = false,
        validator = { validateUrl(it) },
        validationMessage = "Invalid Home URL provided.",
        onSave = { userSettings.homeUrl = it },
        extraContent = { setValue: (String) -> Unit ->
            if (restricted) {
                return@TextSettingFieldItem
            }
            val currentUrl = systemSettings.currentUrl
            Button(
                onClick = { setValue(currentUrl) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                shape = RoundedCornerShape(4.dp),
                colors = ButtonDefaults.buttonColors()
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Use the current URL:",
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
        }
    )
}
