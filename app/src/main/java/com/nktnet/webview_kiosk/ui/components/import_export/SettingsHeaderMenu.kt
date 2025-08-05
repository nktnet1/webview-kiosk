package com.nktnet.webview_kiosk.ui.components.import_export

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import com.nktnet.webview_kiosk.R
import com.nktnet.webview_kiosk.config.UserSettings

@Composable
 fun SettingsHeaderMenu() {
    val tintColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
    var showMenu by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }
    var showImportDialog by remember { mutableStateOf(false) }
    var importText by remember { mutableStateOf("") }
    var importError by remember { mutableStateOf(false) }

    var showExportDialog by remember { mutableStateOf(false) }
    var exportText by remember { mutableStateOf("") }

    val toastRef = remember { mutableStateOf<Toast?>(null) }
    fun showToast(message: String) {
        toastRef.value?.cancel()
        toastRef.value = Toast.makeText(context, message, Toast.LENGTH_SHORT).also { it.show() }
    }

    ImportSettingsDialog(
        showDialog = showImportDialog,
        onDismiss = { showImportDialog = false },
        importText = importText,
        onImportTextChange = {
            importText = it
            importError = false
        },
        importError = importError,
        onImportConfirm = {
            val success = userSettings.importFromBase64(importText)
            if (success) {
                showToast("Settings imported successfully")
                showImportDialog = false
            } else {
                importError = true
            }
        }
    )

    ExportSettingsDialog(
        showDialog = showExportDialog,
        onDismiss = { showExportDialog = false },
        exportText = exportText,
        onCopy = {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("Exported Settings", exportText))
            showToast("Copied to clipboard")
        }
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "Settings",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        Box {
            IconButton(onClick = { showMenu = true }) {
                Icon(
                    Icons.Default.MoreVert,
                    contentDescription = "Menu",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false }
            ) {
                DropdownMenuItem(
                    text = { Text("Import", color = tintColor) },
                    onClick = {
                        showMenu = false
                        showImportDialog = true
                        importText = ""
                        importError = false                    },
                    leadingIcon = {
                        Icon(
                            painter = painterResource(R.drawable.outline_drive_folder_upload_24),
                            contentDescription = null,
                            tint = tintColor
                        )
                    }
                )
                DropdownMenuItem(
                    text = { Text("Export", color = tintColor) },
                    onClick = {
                        showMenu = false
                        exportText = userSettings.exportToBase64()
                        showExportDialog = true                    },
                    leadingIcon = {
                        Icon(
                            painter = painterResource(R.drawable.outline_file_export_24),
                            contentDescription = null,
                            tint = tintColor
                        )
                    }
                )
            }
        }
    }
}

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
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onImportConfirm) { Text("Import") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
        title = { Text("Import Settings (Base64)") },
        text = {
            Column {
                OutlinedTextField(
                    value = importText,
                    onValueChange = {
                        onImportTextChange(it)
                    },
                    placeholder = { Text("Paste your exported Base64 string here") },
                    isError = importError,
                    modifier = Modifier.fillMaxWidth()
                )
                if (importError) {
                    Text(
                        "Invalid input or corrupted data",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    )
}

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
            TextButton(onClick = {
                onCopy()
                onDismiss()
            }) {
                Text("Copy")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
