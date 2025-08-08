package com.nktnet.webview_kiosk.ui.view

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.nktnet.webview_kiosk.config.UserSettings
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

    val saveEnabled = true

    val toastRef = remember { mutableStateOf<android.widget.Toast?>(null) }

    fun showToast(message: String) {
        toastRef.value?.cancel()
        toastRef.value = android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT).also { it.show() }
    }

    fun saveSettings() {
        userSettings.allowRefresh = allowRefresh
        userSettings.allowBackwardsNavigation = allowBackwardsNavigation
        showToast("Settings saved successfully.")
    }

    Column(modifier = Modifier.fillMaxSize()) {
        SettingLabel(navController = navController, label = "Web Browsing")

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "Allow Pull-to-Refresh", style = MaterialTheme.typography.bodyLarge)
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

        Spacer(modifier = Modifier.height(24.dp))

        SettingsActionButtons(
            navController = navController,
            saveEnabled = saveEnabled,
            saveSettings = { saveSettings() }
        )
    }
}
