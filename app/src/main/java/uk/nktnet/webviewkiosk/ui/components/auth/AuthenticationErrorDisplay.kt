package uk.nktnet.webviewkiosk.ui.components.auth

import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import uk.nktnet.webviewkiosk.managers.AuthenticationManager

@Composable
fun AuthenticationErrorDisplay(
    errorResult: AuthenticationManager.AuthenticationResult?,
    onRetry: () -> Unit,
    onCancel: (() -> Unit)? = null,
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
                is AuthenticationManager.AuthenticationResult.AuthenticationError ->
                    "Error: ${errorResult.error}"
                AuthenticationManager.AuthenticationResult.AuthenticationNotSet ->
                    "No biometric or credentials enrolled"
                AuthenticationManager.AuthenticationResult.AuthenticationFailed ->
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
                onClick = {
                    onCancel?.invoke() ?: dispatcher?.onBackPressed()
                },
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
