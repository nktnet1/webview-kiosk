package com.nktnet.webview_kiosk.ui.view

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.nktnet.webview_kiosk.config.UserSettings
import com.nktnet.webview_kiosk.ui.components.common.LabelWithInfo
import com.nktnet.webview_kiosk.ui.components.setting.SettingDivider
import com.nktnet.webview_kiosk.ui.components.setting.SettingLabel
import com.nktnet.webview_kiosk.ui.components.setting.SettingsActionButtons

@Composable
fun SettingsWebBrowsingScreen(
    navController: NavController,
) {
    val context = LocalContext.current
    val userSettings = UserSettings(context)

    var allowRefresh by remember { mutableStateOf(userSettings.allowRefresh) }
    var allowBackwardsNavigation by remember { mutableStateOf(userSettings.allowBackwardsNavigation) }
    var allowGoHome by remember { mutableStateOf(userSettings.allowGoHome) }
    var allowOtherUrlSchemes by remember { mutableStateOf(userSettings.allowOtherUrlSchemes) }
    var clearHistoryOnHome by remember { mutableStateOf(userSettings.clearHistoryOnHome) }
    var searchProviderUrl by remember { mutableStateOf(TextFieldValue(userSettings.searchProviderUrl)) }

    var searchProviderError by remember { mutableStateOf<String?>(null) }

    val saveEnabled = true

    val toastRef = remember { mutableStateOf<android.widget.Toast?>(null) }

    fun validateSearchProviderUrl(input: String): String? {
        return if (!input.contains("{query}") && !input.contains("q=")) {
            "Search provider URL should contain a query parameter (e.g. q= or {query})"
        } else null
    }

    fun showToast(message: String) {
        toastRef.value?.cancel()
        toastRef.value =
            android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT)
                .also { it.show() }
    }

    fun saveSettings() {
        val error = validateSearchProviderUrl(searchProviderUrl.text.trim())
        searchProviderError = error
        if (error == null) {
            userSettings.allowRefresh = allowRefresh
            userSettings.allowBackwardsNavigation = allowBackwardsNavigation
            userSettings.allowGoHome = allowGoHome
            userSettings.allowOtherUrlSchemes = allowOtherUrlSchemes
            userSettings.clearHistoryOnHome = clearHistoryOnHome
            userSettings.searchProviderUrl = searchProviderUrl.text.trim()
            showToast("Settings saved successfully.")
        } else {
            showToast("Please fix errors before saving.")
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .windowInsetsPadding(WindowInsets.safeContent)
            .verticalScroll(rememberScrollState()),
    ) {
        SettingLabel(navController = navController, label = "Web Browsing")

        SettingDivider()

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            LabelWithInfo(
                label = "Allow Refresh",
                infoTitle = "Allow Refresh",
                infoText = "Whether the user can pull down at the top of a webpage to refresh."
            )
            Switch(
                checked = allowRefresh,
                onCheckedChange = { allowRefresh = it }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            LabelWithInfo(
                label = "Allow Backwards Navigation",
                infoTitle = "Allow Backwards Navigation",
                infoText = "Whether the user can use the device 'back' button to go back one page in history."
            )
            Switch(
                checked = allowBackwardsNavigation,
                onCheckedChange = { allowBackwardsNavigation = it }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            LabelWithInfo(
                label = "Allow Go Home (multi tap)",
                infoTitle = "Allow Go Home",
                infoText = "Whether the user can return to the configured home page when tapping the screen 10 times in quick succession."
            )
            Switch(
                checked = allowGoHome,
                onCheckedChange = { allowGoHome = it }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            LabelWithInfo(
                label = "Clear History on Home",
                infoTitle = "Clear History on Home",
                infoText = "Clear the browser history whenever the user triggers an action to return home."
            )
            Switch(
                checked = clearHistoryOnHome,
                onCheckedChange = { clearHistoryOnHome = it }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            LabelWithInfo(
                label = "Open Other URL Schemes",
                infoTitle = "Open Other URL Schemes",
                infoText = "Allow opening of non-http/https URL schemes such as 'tel:', 'mailto:' and 'intent:' in other apps."
            )
            Switch(
                checked = allowOtherUrlSchemes,
                onCheckedChange = { allowOtherUrlSchemes = it }
            )
        }


        LabelWithInfo(
            modifier = Modifier.padding(top = 20.dp, bottom = 4.dp),
            label = "Search Provider URL",
            infoTitle = "Search Provider URL",
            infoText = "The URL used for search queries in the address bar. This URL must include the query parameter, e.g.\n\thttps://google.com/search?q="
        )
        OutlinedTextField(
            value = searchProviderUrl,
            onValueChange = {
                searchProviderUrl = it
                searchProviderError = validateSearchProviderUrl(it.text.trim())
            },
            isError = searchProviderError != null,
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        if (searchProviderError != null) {
            Text(
                text = searchProviderError ?: "",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(start = 16.dp, top = 4.dp)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        SettingsActionButtons(
            navController = navController,
            saveEnabled = saveEnabled && searchProviderError == null,
            saveSettings = { saveSettings() }
        )
    }
}
