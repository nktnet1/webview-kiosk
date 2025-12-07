package uk.nktnet.webviewkiosk.ui.screens

import android.os.Build
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.webkit.WebView
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
import uk.nktnet.webviewkiosk.managers.ToastManager
import uk.nktnet.webviewkiosk.ui.components.setting.SettingDivider
import uk.nktnet.webviewkiosk.ui.components.setting.SettingLabel
import uk.nktnet.webviewkiosk.utils.openAppDetailsSettings
import uk.nktnet.webviewkiosk.utils.openDataUsageSettings
import uk.nktnet.webviewkiosk.utils.openDefaultAppsSettings
import uk.nktnet.webviewkiosk.utils.openDefaultLauncherSettings
import uk.nktnet.webviewkiosk.utils.openSettings
import uk.nktnet.webviewkiosk.utils.openWifiSettings
import uk.nktnet.webviewkiosk.utils.webview.WebViewNavigation

@Composable
fun SettingsMoreActionsScreen(navController: NavController) {
    val context = LocalContext.current
    val systemSettings = SystemSettings(context)

    val webView = remember { WebView(context) }

    DisposableEffect(webView) {
        onDispose {
            webView.stopLoading()
            webView.removeAllViews()
            webView.destroy()
        }
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
            Spacer(modifier = Modifier.height(16.dp))

            SectionHeader("Shortcuts")
            ActionButton("App Info") { openAppDetailsSettings(context) }
            ActionButton("Default Launcher") {
                openDefaultLauncherSettings(context)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                ActionButton("Default Apps") {
                    openDefaultAppsSettings(context)
                }
            }
            ActionButton("WiFi Settings") {
                openWifiSettings(context)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                ActionButton("Data Usage") {
                    openDataUsageSettings(context)
                }
            }
            ActionButton("Device Settings") {
                openSettings(context)
            }

            Spacer(modifier = Modifier.height(20.dp))

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

            Spacer(modifier = Modifier.height(20.dp))

            SectionHeader("Clear")
            ActionButton("Clear Cookies") {
                CookieManager.getInstance().removeAllCookies(null)
                CookieManager.getInstance().flush()
                ToastManager.show(context, "Cookies cleared.")
            }
            ActionButton("Clear Cache") {
                webView.clearCache(true)
                ToastManager.show(context, "Cache cleared.")
            }
            ActionButton("Clear Form Data") {
                webView.clearFormData()
                ToastManager.show(context, "Form data cleared.")
            }
            ActionButton("Clear History") {
                webView.clearHistory()
                WebViewNavigation.clearHistory(systemSettings)
                ToastManager.show(context, "History cleared.")
            }
            ActionButton("Clear SSL Preferences") {
                webView.clearSslPreferences()
                ToastManager.show(context, "SSL preferences cleared.")
            }
            ActionButton("Clear Web Storage") {
                WebStorage.getInstance().deleteAllData()
                ToastManager.show(context, "Web storage cleared.")
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
        modifier = Modifier.padding(bottom = 8.dp)
    )
    HorizontalDivider(
        Modifier.padding(bottom = 8.dp),
        DividerDefaults.Thickness,
        DividerDefaults.color
    )
}
