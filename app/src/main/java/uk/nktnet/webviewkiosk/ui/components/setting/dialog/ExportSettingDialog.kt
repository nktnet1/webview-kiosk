package uk.nktnet.webviewkiosk.ui.components.setting.dialog

import android.content.ClipData
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.toClipEntry
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.launch
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.managers.ToastManager

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
    val jsonText by remember {
        derivedStateOf {
            if (prettyJson) {
                rawJson.toString(2)
            } else {
                rawJson.toString()
            }
        }
    }

    var selectedTab by remember { mutableStateOf(ExportTab.Base64) }
    val tabs = ExportTab.entries.toTypedArray()
    val textDisplay = if (selectedTab == ExportTab.Base64) {
        base64Text
    } else {
        jsonText
    }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(
            if (selectedTab == ExportTab.Base64) {
                "text/plain"
            } else {
                "application/json"
            }
        )
    ) { uri ->
        if (uri != null) {
            scope.launch {
                try {
                    context.contentResolver.openOutputStream(uri)?.use {
                        it.write(textDisplay.toByteArray())
                        println("[DEBUG] $textDisplay")
                        ToastManager.show(context, "Exported to $uri")
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    ToastManager.show(context, "Export Error: ${e.message}")
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
                        text = textDisplay,
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
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    TextButton(
                        onClick = {
                            scope.launch {
                                exportLauncher.launch("wk_user_settings")
                            }
                        },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.tertiary
                        ),
                    ) {
                        Text("Save File")
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
                        TextButton(
                            onClick = {
                                scope.launch {
                                    val clipData = ClipData.newPlainText(
                                        "Exported Data",
                                        textDisplay
                                    )
                                    clipboard.setClipEntry(clipData.toClipEntry())
                                    onDismiss()
                                }
                            },
                        ) {
                            Text("Copy")
                        }
                    }
                }
            }
        }
    }
}
