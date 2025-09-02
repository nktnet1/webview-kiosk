package com.nktnet.webview_kiosk.ui.components.setting

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.navigation.NavController
import com.nktnet.webview_kiosk.R
import com.nktnet.webview_kiosk.config.option.ThemeOption
import com.nktnet.webview_kiosk.config.UserSettings
import androidx.core.net.toUri
import com.nktnet.webview_kiosk.ui.components.setting.dialog.ExportSettingsDialog
import com.nktnet.webview_kiosk.ui.components.setting.dialog.ImportSettingsDialog

@Composable
fun SettingsHeaderMenu(
    navController: NavController,
    themeState: MutableState<ThemeOption>,
) {
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
                themeState.value = userSettings.theme
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
        SettingLabel(
            navController = navController,
            label = "Settings",
            endIcon = {
                Box(
                    modifier = Modifier.align(Alignment.Bottom)
                ) {
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
                                importError = false
                            },
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
                                showExportDialog = true
                            },
                            leadingIcon = {
                                Icon(
                                    painter = painterResource(R.drawable.outline_file_export_24),
                                    contentDescription = null,
                                    tint = tintColor
                                )
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Help", color = tintColor) },
                            onClick = {
                                showMenu = false
                                val intent = Intent(Intent.ACTION_VIEW,
                                    "https://webviewkiosk.nktnet.uk".toUri())
                                context.startActivity(intent)
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = null,
                                    tint = tintColor
                                )
                            }
                        )
                    }
                }
            }
        )
    }
}
