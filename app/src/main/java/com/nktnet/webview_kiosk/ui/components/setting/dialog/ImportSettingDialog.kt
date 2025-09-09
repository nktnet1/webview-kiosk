package com.nktnet.webview_kiosk.ui.components.setting.dialog

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.nktnet.webview_kiosk.R

@Composable
fun ImportSettingsDialog(
    showDialog: Boolean,
    onDismiss: () -> Unit,
    importText: String,
    onImportTextChange: (String) -> Unit,
    importError: Boolean,
    onImportConfirm: () -> Unit
) {
    if (!showDialog) return
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.padding(bottom = 16.dp),
        confirmButton = {
            TextButton(
                onClick = onImportConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Text("Import")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
        title = { Text("Import Settings (Base64)") },
        text = {
            Column {
                OutlinedTextField(
                    value = importText,
                    onValueChange = onImportTextChange,
                    placeholder = { Text("Paste your exported Base64 string here.") },
                    isError = importError,
                    minLines = 10,
                    modifier = Modifier.fillMaxWidth()
                )
                if (importError) {
                    Text(
                        "Invalid input or corrupted data",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { onImportTextChange("") },
                    ) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = "Clear",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                    IconButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clipData = clipboard.primaryClip
                            val pasteData = clipData?.getItemAt(0)?.text?.toString() ?: ""
                            onImportTextChange(pasteData)
                        }
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.outline_content_paste_24),
                            contentDescription = "Paste",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    )
}
