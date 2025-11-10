package uk.nktnet.webviewkiosk.ui.components.auth

import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import uk.nktnet.webviewkiosk.auth.BiometricPromptManager

@Composable
fun AuthenticationErrorDisplay(
    errorResult: BiometricPromptManager.BiometricResult?,
    onRetry: () -> Unit
) {
    val dispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher

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
                    "Error: ${errorResult.error}"
                BiometricPromptManager.BiometricResult.HardwareUnavailable ->
                    "Biometric hardware unavailable"
                BiometricPromptManager.BiometricResult.AuthenticationNotSet ->
                    "No biometric or credentials enrolled"
                BiometricPromptManager.BiometricResult.AuthenticationFailed ->
                    "Authentication failed"
                else -> errorResult.toString()
            },
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = { dispatcher?.onBackPressed() },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary,
                    contentColor = MaterialTheme.colorScheme.onSecondary
                ),
                modifier = Modifier.defaultMinSize(minWidth = 100.dp)
            ) {
                Text("Cancel")
            }

            Button(
                onClick = onRetry,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                modifier = Modifier.defaultMinSize(minWidth = 100.dp)
            ) {
                Text("Retry")
            }
        }

    }
}
