package com.nktnet.webview_kiosk.ui.components.setting.dialog

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

@Composable
fun ExportSettingsDialog(
    showDialog: Boolean,
    onDismiss: () -> Unit,
    exportText: String,
    onCopy: () -> Unit
) {
    if (!showDialog) return
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Exported Settings (Base64)") },
        text = { Text(exportText, style = MaterialTheme.typography.bodySmall) },
        confirmButton = {
            TextButton(
                onClick = {
                    onCopy()
                    onDismiss()
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