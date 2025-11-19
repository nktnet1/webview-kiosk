package uk.nktnet.webviewkiosk.ui.screens

import android.webkit.CookieManager
import android.webkit.WebStorage
import android.webkit.WebView
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import uk.nktnet.webviewkiosk.config.Screen
import uk.nktnet.webviewkiosk.config.SystemSettings
import uk.nktnet.webviewkiosk.ui.components.setting.SettingDivider
import uk.nktnet.webviewkiosk.ui.components.setting.SettingLabel
import uk.nktnet.webviewkiosk.utils.openAppDetailsSettings
import uk.nktnet.webviewkiosk.utils.webview.WebViewNavigation

@Composable
fun MoreActionsScreen(navController: NavController) {
    val context = LocalContext.current
    val systemSettings = SystemSettings(context)

    var toastRef: Toast? by remember { mutableStateOf(null) }
    val showToast: (String) -> Unit = { msg ->
        toastRef?.cancel()
        toastRef = Toast.makeText(
            context, msg, Toast.LENGTH_SHORT
        ).apply { show() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeContent)
            .padding(horizontal = 16.dp)
    ) {
        SettingLabel(navController = navController, label = "More Actions")
        SettingDivider()

        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .fillMaxSize()
        ) {
            ActionButton("Open App Info") { openAppDetailsSettings(context) }

            SectionHeader("Manage")
            ActionButton("Local Files") {
                navController.navigate(Screen.SettingsWebContentFiles.route)
            }
            ActionButton("Site Permissions") {
                navController.navigate(Screen.SettingsWebBrowsingSitePermissions.route)
            }
            ActionButton("Device Owner") {
                navController.navigate(Screen.SettingsDeviceOwner.route)
            }

            SectionHeader("Clear")
            ActionButton("Clear Cookies") {
                CookieManager.getInstance().removeAllCookies(null)
                CookieManager.getInstance().flush()
                showToast("Cookies cleared.")
            }
            ActionButton("Clear Cache") {
                val webView = WebView(context)
                webView.clearCache(true)
                webView.destroy()
                showToast("Cache cleared.")
            }
            ActionButton("Clear Form Data") {
                val webView = WebView(context)
                webView.clearFormData()
                webView.destroy()
                showToast("Form data cleared.")
            }
            ActionButton("Clear History") {
                val webView = WebView(context)
                webView.clearHistory()
                WebViewNavigation.clearHistory(systemSettings)
                webView.destroy()
                showToast("History cleared.")
            }
            ActionButton("Clear SSL Preferences") {
                val webView = WebView(context)
                webView.clearSslPreferences()
                webView.destroy()
                showToast("SSL preferences cleared.")
            }
            ActionButton("Clear Web Storage") {
                WebStorage.getInstance().deleteAllData()
                showToast("Web storage cleared.")
            }

            Spacer(modifier = Modifier.padding(bottom = 10.dp))
        }
    }
}

@Composable
private fun ActionButton(
    label: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Button(
        enabled = enabled,
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
    ) { Text(label) }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(top = 22.dp, bottom = 8.dp)
    )
    HorizontalDivider(
        Modifier.padding(bottom = 8.dp),
        DividerDefaults.Thickness,
        DividerDefaults.color
    )
}
