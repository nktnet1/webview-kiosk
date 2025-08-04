package com.nktnet.webview_kiosk.ui.view

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.nktnet.webview_kiosk.auth.BiometricPromptManager
import com.nktnet.webview_kiosk.config.UserSettings
import com.nktnet.webview_kiosk.ui.components.AuthenticationErrorDisplay
import com.nktnet.webview_kiosk.ui.components.RequireAuthentication
import com.nktnet.webview_kiosk.ui.components.SettingsContent


@Composable
fun SettingsScreen(
    onClose: () -> Unit,
    promptManager: BiometricPromptManager
) {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }

    RequireAuthentication(
        promptManager = promptManager,
        onAuthenticated = {
            SettingsContent(userSettings = userSettings, onClose = onClose)
        },
        onFailed = { errorResult ->
            AuthenticationErrorDisplay(errorResult = errorResult) {
                promptManager.showBiometricPrompt(
                    title = "Authentication Required",
                    description = "Please authenticate to modify settings"
                )
            }
        }
    )
}
