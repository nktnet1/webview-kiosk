package uk.nktnet.webviewkiosk.ui.components.webview

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.progressSemantics
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

@Composable
fun DownloadProgressDialog(
    progress: Float,
    onCancel: () -> Unit,
    onBackground: () -> Unit
) {
    Dialog(onDismissRequest = { onCancel() }) {
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .widthIn(min = 200.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Downloading...", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(12.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .progressSemantics(progress),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                )
                Spacer(Modifier.height(8.dp))
                Text("${(progress * 100).toInt()}%", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(12.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(onClick = { onCancel() }) {
                        Text("Cancel")
                    }
                    OutlinedButton(onClick = { onBackground() }) {
                        Text("Background")
                    }
                }
            }
        }
    }
}
