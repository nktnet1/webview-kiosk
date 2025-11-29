package uk.nktnet.webviewkiosk.ui.components.setting.dialog

import android.content.ClipData
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.toClipEntry
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import uk.nktnet.webviewkiosk.config.UserSettings

@Composable
fun ExportSettingsDialog(
    showDialog: Boolean,
    onDismiss: () -> Unit,
) {
    if (!showDialog) return

    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()

    val exportText = remember { userSettings.exportToBase64() }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.padding(bottom = 16.dp),
        title = { Text("Export Settings (Base64)") },
        text = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Text(exportText, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    scope.launch {
                        val clipData = ClipData.newPlainText("Exported Data", exportText)
                        clipboard.setClipEntry(clipData.toClipEntry())
                        onDismiss()
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Text("Copy")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
