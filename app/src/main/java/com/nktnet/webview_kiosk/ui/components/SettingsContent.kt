package com.nktnet.webview_kiosk.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.nktnet.webview_kiosk.R
import com.nktnet.webview_kiosk.config.UserSettings
import com.nktnet.webview_kiosk.utils.validateMultilineRegex
import com.nktnet.webview_kiosk.utils.validateUrl

@Composable
fun SettingsContent(
    userSettings: UserSettings,
    onClose: () -> Unit,
) {
    val context = LocalContext.current

    var homeUrl by remember { mutableStateOf(userSettings.homeUrl) }
    var blacklist by remember { mutableStateOf(userSettings.websiteBlacklist) }
    var whitelist by remember { mutableStateOf(userSettings.websiteWhitelist) }
    var blockedMessage by remember { mutableStateOf(userSettings.blockedMessage) }

    var homeUrlError by remember { mutableStateOf(false) }
    var blacklistError by remember { mutableStateOf(false) }
    var whitelistError by remember { mutableStateOf(false) }

    val saveEnabled = !homeUrlError && !blacklistError && !whitelistError

    var showExportDialog by remember { mutableStateOf(false) }
    var exportText by remember { mutableStateOf("") }

    var showImportDialog by remember { mutableStateOf(false) }
    var importText by remember { mutableStateOf("") }
    var importError by remember { mutableStateOf(false) }

    var showMenu by remember { mutableStateOf(false) }

    val toastRef = remember { mutableStateOf<Toast?>(null) }

    fun showToast(message: String) {
        toastRef.value?.cancel()
        toastRef.value = Toast.makeText(context, message, Toast.LENGTH_SHORT).also { it.show() }
    }

    fun saveSettings() {
        userSettings.homeUrl = homeUrl
        userSettings.websiteBlacklist = blacklist
        userSettings.websiteWhitelist = whitelist
        userSettings.blockedMessage = blockedMessage.trim()
        showToast("Settings saved successfully.")
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 30.dp, start = 16.dp, end = 16.dp, bottom = 16.dp)
    ) {
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
                val tintColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Import", color = tintColor) },
                        onClick = {
                            importText = ""
                            importError = false
                            showImportDialog = true
                            showMenu = false
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
                            exportText = userSettings.exportToBase64()
                            showExportDialog = true
                            showMenu = false
                        },
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

        LabelWithInfo(
            label = "Home URL",
            infoTitle = "Home URL",
            infoText = "The URL that can be reset to using the floating icon button."
        )
        UrlInput(
            value = homeUrl,
            onValueChange = {
                homeUrl = it
                homeUrlError = !validateUrl(it)
            },
            isError = homeUrlError
        )

        LabelWithInfo(
            label = "Blacklist Regex",
            infoTitle = "Blacklist",
            infoText = """
                Specify regular expressions (regex), one per line, to allow matching URLs.
                Escaping is required for special characters in regex like '.' and '?'.

                These patterns also use partial (contains) matching by default.

                If you need strict control, anchor your regex with '^' and '$'.

                Examples:
                - .*
                - ^https://.*\\.?google\\.com/.*
                Whitelist patterns take precedence over blacklist patterns.
            """.trimIndent()
        )
        PatternInput(
            value = blacklist,
            onValueChange = {
                blacklist = it
                blacklistError = !validateMultilineRegex(it)
            },
            isError = blacklistError,
            placeholder = "e.g.\n\t^.*$\n\t^https://.*\\.?google\\.com/.*"
        )

        LabelWithInfo(
            label = "Whitelist Regex",
            infoTitle = "Whitelist",
            infoText = """
                Specify regular expressions (regex), one per line, to allow matching URLs.
                Escaping is required for special characters in regex like '.' and '?'.

                These patterns also use partial (contains) matching by default.

                If you need strict control, anchor your regex with '^' and '$'.

                Examples:
                - ^https://allowedsite\\.com$
                - ^https://.*\\.trusted\\.org/.*
                Whitelist patterns take precedence over blacklist patterns.
            """.trimIndent()
        )
        PatternInput(
            value = whitelist,
            onValueChange = {
                whitelist = it
                whitelistError = !validateMultilineRegex(it)
            },
            isError = whitelistError,
            placeholder = "e.g.\n\t^https://allowedsite\\.com/.*\n\t^https://.*\\.trusted\\.org/.*"
        )

        LabelWithInfo(
            label = "Blocked Message",
            infoTitle = "Blocked Message",
            infoText = "Custom message shown on blocked pages. Can be left empty."
        )
        OutlinedTextField(
            value = blockedMessage,
            onValueChange = { blockedMessage = it },
            placeholder = { Text("e.g. This site is blocked by <Company Name>") },
            isError = false,
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
            maxLines = 5,
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 32.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                onClick = onClose,
                modifier = Modifier.width(150.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error)
            ) {
                Text("Cancel")
            }

            OutlinedButton(
                enabled = saveEnabled,
                modifier = Modifier.width(150.dp),
                onClick = { saveSettings() },
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.primary
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
            ) {
                Text("Save")
            }
        }

        Button(
            onClick = {
                saveSettings()
                onClose()
            },
            modifier = Modifier
                .padding(top = 2.dp)
                .width(150.dp)
                .align(Alignment.End),
            enabled = saveEnabled,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        ) {
            Text("Save & Close")
        }

        DeviceSecurityTip()
    }

    if (showExportDialog) {
        AlertDialog(
            onDismissRequest = { showExportDialog = false },
            title = { Text("Exported Settings (Base64)") },
            text = {
                Column {
                    Text(exportText, style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("Exported Settings", exportText))
                    showExportDialog = false
                }) {
                    Text("Copy")
                }
            },
            dismissButton = {
                TextButton(onClick = { showExportDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showImportDialog) {
        AlertDialog(
            onDismissRequest = { showImportDialog = false },
            confirmButton = {
                TextButton(onClick = {
                    val success = userSettings.importFromBase64(importText)
                    if (success) {
                        homeUrl = userSettings.homeUrl
                        blacklist = userSettings.websiteBlacklist
                        whitelist = userSettings.websiteWhitelist
                        blockedMessage = userSettings.blockedMessage
                        importError = false
                        showImportDialog = false
                        showToast("Settings imported successfully")
                    } else {
                        importError = true
                    }
                }) {
                    Text("Import")
                }
            },
            dismissButton = {
                TextButton(onClick = { showImportDialog = false }) {
                    Text("Cancel")
                }
            },
            title = { Text("Import Settings (Base64)") },
            text = {
                Column {
                    OutlinedTextField(
                        value = importText,
                        onValueChange = {
                            importText = it
                            importError = false
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
}
