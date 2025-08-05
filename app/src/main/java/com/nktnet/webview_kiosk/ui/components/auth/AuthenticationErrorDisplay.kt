package com.nktnet.webview_kiosk.ui.components.auth

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nktnet.webview_kiosk.auth.BiometricPromptManager

@Composable
fun AuthenticationErrorDisplay(
    errorResult: BiometricPromptManager.BiometricResult?,
    onRetry: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = when (errorResult) {
                is BiometricPromptManager.BiometricResult.AuthenticationError ->
                    "Authentication Error: ${errorResult.error}"
                BiometricPromptManager.BiometricResult.HardwareUnavailable ->
                    "Biometric hardware unavailable"
                BiometricPromptManager.BiometricResult.FeatureUnavailable ->
                    "Biometric feature not available"
                BiometricPromptManager.BiometricResult.AuthenticationNotSet ->
                    "No biometric or credentials enrolled"
                BiometricPromptManager.BiometricResult.AuthenticationFailed ->
                    "Authentication failed"
                else -> "Authentication failed"
            },
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        Button(onClick = onRetry) {
            Text("Retry Authentication")
        }
    }
}

