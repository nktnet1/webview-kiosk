package uk.nktnet.webviewkiosk.ui.screens

import android.webkit.CookieManager
import android.webkit.WebStorage
import android.webkit.WebView
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import uk.nktnet.webviewkiosk.config.Screen
import uk.nktnet.webviewkiosk.config.SystemSettings
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
        toastRef = Toast.makeText(context, msg, Toast.LENGTH_SHORT).apply { show() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .windowInsetsPadding(WindowInsets.safeContent)
    ) {
        SettingLabel(navController = navController, label = "More Actions")

        HorizontalDivider(
            modifier = Modifier.padding(vertical = 8.dp),
            thickness = DividerDefaults.Thickness,
            color = DividerDefaults.color
        )

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                openAppDetailsSettings(context)
            }
        ) {
            Text(
                text = "Open App Info",
                textAlign = TextAlign.Center,
            )
        }

        Text(
            text = "Manage",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 22.dp, bottom = 8.dp)
        )
        HorizontalDivider(
            Modifier.padding(bottom = 8.dp),
            DividerDefaults.Thickness,
            DividerDefaults.color
        )

        Button(
            onClick = { navController.navigate(Screen.SettingsWebContentFiles.route) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp),
        ) {
            Text("Local Files")
        }

        Button(
            onClick = { navController.navigate(
                Screen.SettingsWebBrowsingSitePermissions.route)
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp),
        ) {
            Text("Site Permissions")
        }

        Text(
            text = "Clear",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 22.dp, bottom = 8.dp)
        )
        HorizontalDivider(
            Modifier.padding(bottom = 8.dp),
            DividerDefaults.Thickness,
            DividerDefaults.color
        )

        Button(
            onClick = {
                CookieManager.getInstance().removeAllCookies(null)
                CookieManager.getInstance().flush()
                showToast("Cookies cleared.")
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp)
        ) {
            Text("Clear Cookies")
        }

        Button(
            onClick = {
                val webView = WebView(context)
                webView.clearCache(true)
                webView.destroy()
                showToast("Cache cleared.")
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp)
        ) {
            Text("Clear Cache")
        }

        Button(
            onClick = {
                val webView = WebView(context)
                webView.clearFormData()
                webView.destroy()
                showToast("Form data cleared.")
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp)
        ) {
            Text("Clear Form Data")
        }

        Button(
            onClick = {
                val webView = WebView(context)
                webView.clearHistory()
                WebViewNavigation.clearHistory(systemSettings)
                webView.destroy()
                showToast("History cleared.")
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp)
        ) {
            Text("Clear History")
        }

        Button(
            onClick = {
                val webView = WebView(context)
                webView.clearSslPreferences()
                webView.destroy()
                showToast("SSL preferences cleared.")
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp)
        ) {
            Text("Clear SSL Preferences")
        }

        Button(
            onClick = {
                WebStorage.getInstance().deleteAllData()
                showToast("Web storage cleared.")
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp)
        ) {
            Text("Clear Web Storage")
        }
    }
}
