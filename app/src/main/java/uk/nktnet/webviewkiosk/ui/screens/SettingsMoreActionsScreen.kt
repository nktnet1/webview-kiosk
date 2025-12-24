package com.nktnet.webview_kiosk.ui.screens

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
import com.nktnet.webview_kiosk.R
import com.nktnet.webview_kiosk.config.Screen
import com.nktnet.webview_kiosk.config.SystemSettings
import com.nktnet.webview_kiosk.managers.ToastManager
import com.nktnet.webview_kiosk.ui.components.setting.SettingDivider
import com.nktnet.webview_kiosk.ui.components.setting.SettingLabel
import com.nktnet.webview_kiosk.ui.components.setting.dialog.AppLauncherDialog
import com.nktnet.webview_kiosk.utils.openAppDetailsSettings
import com.nktnet.webview_kiosk.utils.openDataUsageSettings
import com.nktnet.webview_kiosk.utils.openDefaultAppsSettings
import com.nktnet.webview_kiosk.utils.openDefaultLauncherSettings
import com.nktnet.webview_kiosk.utils.openSettings
import com.nktnet.webview_kiosk.utils.openWifiSettings
import com.nktnet.webview_kiosk.utils.webview.WebViewNavigation

@Composable
fun SettingsMoreActionsScreen(navController: NavController) {
    val context = LocalContext.current
    val systemSettings = SystemSettings(context)
    val webView = remember { WebView(context) }
    var showAppLauncherDialog by remember { mutableStateOf(false) }

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
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(top = 4.dp)
            .padding(horizontal = 16.dp),
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

            SectionHeader(
                stringResource(R.string.settings_more_action_section_shortcuts)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ActionButton(
                    stringResource(R.string.settings_more_action_app_info),
                    modifier = Modifier.weight(1f)
                ) { openAppDetailsSettings(context) }
                ActionButton(
                    stringResource(R.string.settings_more_action_device_settings),
                    modifier = Modifier.weight(1f)
                ) { openSettings(context) }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ActionButton(
                    stringResource(R.string.settings_more_action_default_launcher),
                    modifier = Modifier.weight(1f)
                ) { openDefaultLauncherSettings(context) }
                ActionButton(
                    stringResource(R.string.settings_more_action_default_apps),
                    modifier = Modifier.weight(1f)
                ) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        openDefaultAppsSettings(context)
                    } else {
                        ToastManager.show(
                            context,
                            context.getString(
                                R.string.settings_more_action_toast_sdk_version_error,
                                Build.VERSION_CODES.N,
                                Build.VERSION.SDK_INT,
                            )
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ActionButton(
                    stringResource(R.string.settings_more_action_wifi_settings),
                    modifier = Modifier.weight(1f)
                ) { openWifiSettings(context) }
                ActionButton(
                    stringResource(R.string.settings_more_action_data_usage),
                    modifier = Modifier.weight(1f)
                ) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        openDataUsageSettings(context)
                    } else {
                        ToastManager.show(
                            context,
                            context.getString(
                                R.string.settings_more_action_toast_sdk_version_error,
                                Build.VERSION_CODES.P,
                                Build.VERSION.SDK_INT
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            SectionHeader(
                stringResource(R.string.settings_more_action_section_manage)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ActionButton(
                    stringResource(R.string.settings_more_action_local_files),
                    modifier = Modifier.weight(1f)
                ) { navController.navigate(Screen.SettingsWebContentFiles.route) }
                ActionButton(
                    stringResource(R.string.settings_more_action_site_permissions),
                    modifier = Modifier.weight(1f)
                ) { navController.navigate(Screen.SettingsWebBrowsingSitePermissions.route) }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ActionButton(
                    stringResource(R.string.settings_more_action_device_owner),
                    modifier = Modifier.weight(1f)
                ) { navController.navigate(Screen.SettingsDeviceOwner.route) }
                ActionButton(
                    stringResource(R.string.settings_more_action_app_launcher),
                    modifier = Modifier.weight(1f)
                ) { showAppLauncherDialog = true }
            }

            Spacer(modifier = Modifier.height(16.dp))
            SectionHeader(
                stringResource(R.string.settings_more_action_section_clear)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ActionButton(
                    stringResource(R.string.settings_more_action_clear_cookies),
                    modifier = Modifier.weight(1f)
                ) {
                    CookieManager.getInstance().removeAllCookies(null)
                    CookieManager.getInstance().flush()
                    ToastManager.show(
                        context,
                        context.getString(R.string.settings_more_action_toast_cookies_cleared)
                    )
                }
                ActionButton(
                    stringResource(R.string.settings_more_action_clear_cache),
                    modifier = Modifier.weight(1f)
                ) {
                    webView.clearCache(true)
                    ToastManager.show(
                        context,
                        context.getString(R.string.settings_more_action_toast_cache_cleared)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ActionButton(
                    stringResource(R.string.settings_more_action_clear_form_data),
                    modifier = Modifier.weight(1f)
                ) {
                    webView.clearFormData()
                    ToastManager.show(
                        context,
                        context.getString(R.string.settings_more_action_toast_form_data_cleared)
                    )
                }
                ActionButton(
                    stringResource(R.string.settings_more_action_clear_history),
                    modifier = Modifier.weight(1f)
                ) {
                    webView.clearHistory()
                    WebViewNavigation.clearHistory(systemSettings)
                    ToastManager.show(
                        context,
                        context.getString(R.string.settings_more_action_toast_history_cleared)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ActionButton(
                    stringResource(R.string.settings_more_action_clear_ssl_preferences),
                    modifier = Modifier.weight(1f)
                ) {
                    webView.clearSslPreferences()
                    ToastManager.show(
                        context,
                        context.getString(
                            R.string.settings_more_action_toast_ssl_preferences_cleared
                        )
                    )
                }
                ActionButton(
                    stringResource(R.string.settings_more_action_clear_web_storage),
                    modifier = Modifier.weight(1f)
                ) {
                    WebStorage.getInstance().deleteAllData()
                    ToastManager.show(
                        context,
                        context.getString(
                            R.string.settings_more_action_toast_web_storage_cleared
                        )
                    )
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
