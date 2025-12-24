package com.nktnet.webview_kiosk.ui.components.setting.dialog

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.launch
import com.nktnet.webview_kiosk.R
import com.nktnet.webview_kiosk.config.UserSettings
import com.nktnet.webview_kiosk.managers.ToastManager
import com.nktnet.webview_kiosk.utils.updateDeviceSettings

enum class ImportTab {
    Base64,
    JSON
}

@Composable
fun ImportSettingsDialog(
    showDialog: Boolean,
    onDismiss: () -> Unit,
) {
    if (!showDialog) return

    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()

    var importError by remember { mutableStateOf(false) }
    var importText by remember { mutableStateOf("") }
    var selectedTab by remember { mutableStateOf(ImportTab.Base64) }
    val tabs = ImportTab.entries.toTypedArray()

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            tonalElevation = 6.dp,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 600.dp)
                    .padding(16.dp)
            ) {
                Text(
                    text = "Import Settings",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                PrimaryTabRow(selectedTabIndex = selectedTab.ordinal) {
                    tabs.forEach { tab ->
                        Tab(
                            selected = selectedTab == tab,
                            onClick = { selectedTab = tab },
                            text = { Text(tab.name) },
                            selectedContentColor = MaterialTheme.colorScheme.primary,
                            unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    OutlinedTextField(
                        value = importText,
                        onValueChange = {
                            importText = it
                            importError = false
                        },
                        placeholder = {
                            Text(
                                if (selectedTab == ImportTab.Base64) {
                                    "Paste your Base64 config string."
                                } else {
                                    "Paste your JSON string."
                                }
                            )
                        },
                        isError = importError,
                        minLines = 4,
                        maxLines = 14,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                            .verticalScroll(rememberScrollState())
                    )

                    Spacer(Modifier.height(2.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { importText = "" }) {
                            Icon(
                                painter = painterResource(R.drawable.baseline_clear_24),
                                contentDescription = "Clear",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                        IconButton(onClick = {
                            scope.launch {
                                val clipEntry = clipboard.getClipEntry()
                                importText = clipEntry?.clipData?.getItemAt(0)?.text?.toString() ?: ""
                            }
                        }) {
                            Icon(
                                painter = painterResource(R.drawable.outline_content_paste_24),
                                contentDescription = "Paste",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(
                        onClick = onDismiss,
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("Cancel")
                    }

                    Spacer(Modifier.width(8.dp))

                    TextButton(onClick = {
                        val success = if (selectedTab == ImportTab.Base64) {
                            userSettings.importBase64(importText)
                        } else {
                            userSettings.importJson(importText)
                        }

                        if (success) {
                            updateDeviceSettings(context)
                            ToastManager.show(context, "Imported settings successfully")
                            onDismiss()
                        } else {
                            importError = true
                            ToastManager.show(
                                context,
                                if (selectedTab == ImportTab.Base64) {
                                    "Failed to import Base64."
                                } else {
                                    "Failed to import JSON."
                                }
                            )
                        }
                    }) {
                        Text("Import")
                    }
                }
            }
        }
    }
}
