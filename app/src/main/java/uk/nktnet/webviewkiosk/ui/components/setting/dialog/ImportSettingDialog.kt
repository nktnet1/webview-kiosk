package uk.nktnet.webviewkiosk.ui.components.setting.dialog

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.launch
import uk.nktnet.webviewkiosk.R
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.managers.ToastManager
import uk.nktnet.webviewkiosk.utils.updateDeviceSettings

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

    val fileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                try {
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        importText = stream.bufferedReader().use { it.readText() }
                        importError = false
                    }
                    ToastManager.show(context, "Loaded file successfully.")
                } catch (e: Exception) {
                    e.printStackTrace()
                    ToastManager.show(context, "Failed to read file: ${e.message}")
                }
            }
        }
    }

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
                                importText =
                                    clipEntry?.clipData?.getItemAt(0)?.text?.toString() ?: ""
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
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    TextButton(
                        onClick = {
                            fileLauncher.launch(
                                arrayOf("text/plain", "application/json")
                            )
                        },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.tertiary
                        ),
                    ) {
                        Text("From File")
                    }

                    Row {
                        TextButton(
                            onClick = onDismiss,
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            ),
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
                                        "Failed to import Base64. You may need to switch tabs."
                                    } else {
                                        "Failed to import JSON. You may need to switch tabs."
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
}
