package com.nktnet.webview_kiosk.ui.view

import androidx.compose.foundation.layout.*
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
        toastRef.value = android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT).also { it.show() }
    }

    fun saveSettings() {
        val error = validateSearchProviderUrl(searchProviderUrl.text.trim())
        searchProviderError = error
        if (error == null) {
            userSettings.allowRefresh = allowRefresh
            userSettings.allowBackwardsNavigation = allowBackwardsNavigation
            userSettings.searchProviderUrl = searchProviderUrl.text.trim()
            showToast("Settings saved successfully.")
        } else {
            showToast("Please fix errors before saving.")
        }
    }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        SettingLabel(navController = navController, label = "Web Browsing")

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "Allow Refresh", style = MaterialTheme.typography.bodyLarge)
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
            Text(text = "Allow Backwards Navigation", style = MaterialTheme.typography.bodyLarge)
            Switch(
                checked = allowBackwardsNavigation,
                onCheckedChange = { allowBackwardsNavigation = it }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        LabelWithInfo(
            label = "Search Provider URL",
            infoTitle = "Search Provider URL",
            infoText = "The URL used for search queries. Must include the query parameter, e.g. https://www.google.com/search?q="
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
