package com.nktnet.webview_kiosk.ui.components.setting.dialog

import android.content.ClipData
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.toClipEntry
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.launch
import com.nktnet.webview_kiosk.config.UserSettings

enum class ExportTab {
    Base64,
    JSON
}

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

    val base64Text = remember { userSettings.exportBase64() }
    val rawJson = remember { userSettings.exportJson() }

    var prettyJson by remember { mutableStateOf(true) }
    val jsonText by remember { derivedStateOf { if (prettyJson) rawJson.toString(2) else rawJson.toString() } }

    var selectedTab by remember { mutableStateOf(ExportTab.Base64) }
    val tabs = ExportTab.entries.toTypedArray()

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
                    text = "Export Settings",
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

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = if (selectedTab == ExportTab.Base64) base64Text else jsonText,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(top = 8.dp))

                if (selectedTab == ExportTab.JSON) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Prettify",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Checkbox(
                            checked = prettyJson,
                            onCheckedChange = { prettyJson = it }
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
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
                    TextButton(
                        onClick = {
                            scope.launch {
                                val textToCopy = if (selectedTab == ExportTab.Base64) base64Text else jsonText
                                val clipData = ClipData.newPlainText("Exported Data", textToCopy)
                                clipboard.setClipEntry(clipData.toClipEntry())
                                onDismiss()
                            }
                        }
                    ) {
                        Text("Copy")
                    }
                }
            }
        }
    }
}
