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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import uk.nktnet.webviewkiosk.R
import uk.nktnet.webviewkiosk.config.Screen
import uk.nktnet.webviewkiosk.config.SystemSettings
import uk.nktnet.webviewkiosk.managers.ToastManager
import uk.nktnet.webviewkiosk.ui.components.setting.SettingDivider
import uk.nktnet.webviewkiosk.ui.components.setting.SettingLabel
import uk.nktnet.webviewkiosk.ui.components.setting.dialog.AppLauncherDialog
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
    var showAppLauncherDialog by remember {
        mutableStateOf(false)
    }

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
        SettingLabel(
            navController = navController,
            label = stringResource(R.string.settings_more_actions_title)
        )
        SettingDivider()
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .fillMaxSize()
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            SectionHeader("Shortcuts")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ActionButton("App Info", modifier = Modifier.weight(1f)) {
                    openAppDetailsSettings(context)
                }
                ActionButton("Device Settings", modifier = Modifier.weight(1f)) {
                    openSettings(context)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ActionButton("Default Launcher", modifier = Modifier.weight(1f)) {
                    openDefaultLauncherSettings(context)
                }
                ActionButton("Default Apps", modifier = Modifier.weight(1f)) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        openDefaultAppsSettings(context)
                    } else {
                        ToastManager.show(
                            context,
                            "Error: requires SDK ${Build.VERSION_CODES.N} (current: ${Build.VERSION.SDK_INT}"
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ActionButton("WiFi Settings", modifier = Modifier.weight(1f)) {
                    openWifiSettings(context)
                }
                ActionButton("Data Usage", modifier = Modifier.weight(1f)) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        openDataUsageSettings(context)
                    } else {
                        ToastManager.show(
                            context,
                            "Error: requires SDK ${Build.VERSION_CODES.P} (current: ${Build.VERSION.SDK_INT}"
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            SectionHeader("Manage")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ActionButton("Local Files", modifier = Modifier.weight(1f)) {
                    navController.navigate(Screen.SettingsWebContentFiles.route)
                }
                ActionButton("Site Permissions", modifier = Modifier.weight(1f)) {
                    navController.navigate(Screen.SettingsWebBrowsingSitePermissions.route)
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ActionButton("Device Owner", modifier = Modifier.weight(1f)) {
                    navController.navigate(Screen.SettingsDeviceOwner.route)
                }
                ActionButton("App Launcher", modifier = Modifier.weight(1f)) {
                    showAppLauncherDialog = true
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            SectionHeader("Clear")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ActionButton("Cookies", modifier = Modifier.weight(1f)) {
                    CookieManager.getInstance().removeAllCookies(null)
                    CookieManager.getInstance().flush()
                    ToastManager.show(context, "Cookies cleared.")
                }
                ActionButton("Cache", modifier = Modifier.weight(1f)) {
                    webView.clearCache(true)
                    ToastManager.show(context, "Cache cleared.")
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ActionButton("Form Data", modifier = Modifier.weight(1f)) {
                    webView.clearFormData()
                    ToastManager.show(context, "Form data cleared.")
                }
                ActionButton("History", modifier = Modifier.weight(1f)) {
                    webView.clearHistory()
                    WebViewNavigation.clearHistory(systemSettings)
                    ToastManager.show(context, "History cleared.")
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ActionButton("SSL Preferences", modifier = Modifier.weight(1f)) {
                    webView.clearSslPreferences()
                    ToastManager.show(context, "SSL preferences cleared.")
                }
                ActionButton("Web Storage", modifier = Modifier.weight(1f)) {
                    WebStorage.getInstance().deleteAllData()
                    ToastManager.show(context, "Web storage cleared.")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }

    AppLauncherDialog(
        showDialog = showAppLauncherDialog,
        onDismiss = { showAppLauncherDialog = false }
    )
}

@Composable
private fun ActionButton(
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    color: Color = MaterialTheme.colorScheme.primary,
    onClick: () -> Unit,
) {
    Button(
        enabled = enabled,
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = color),
        modifier = modifier.padding(vertical = 2.dp),
        contentPadding = PaddingValues(vertical = 4.dp, horizontal = 8.dp)
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(bottom = 4.dp)
    )
    HorizontalDivider(
        Modifier.padding(bottom = 4.dp),
        DividerDefaults.Thickness,
        DividerDefaults.color
    )
}
