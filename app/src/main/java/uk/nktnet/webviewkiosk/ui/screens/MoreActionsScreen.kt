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
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import uk.nktnet.webviewkiosk.config.SystemSettings
import uk.nktnet.webviewkiosk.ui.components.setting.SettingLabel
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

        Spacer(modifier = Modifier.height(4.dp))

        Button(
            onClick = {
                CookieManager.getInstance().removeAllCookies(null)
                CookieManager.getInstance().flush()
                showToast("Cookies cleared.")
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
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
                .padding(vertical = 4.dp)
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
                .padding(vertical = 4.dp)
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
                .padding(vertical = 4.dp)
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
                .padding(vertical = 4.dp)
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
                .padding(vertical = 4.dp)
        ) {
            Text("Clear Web Storage")
        }
    }
}
