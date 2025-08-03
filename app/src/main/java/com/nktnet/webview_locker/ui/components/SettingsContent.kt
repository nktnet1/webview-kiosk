package com.nktnet.webview_locker.ui.components

import android.util.Base64
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nktnet.webview_locker.config.UserSettings
import com.nktnet.webview_locker.utils.validateMultilineRegex
import com.nktnet.webview_locker.utils.validateUrl
import org.json.JSONObject

@Composable
fun SettingsContent(
    userSettings: UserSettings,
    onClose: () -> Unit,
) {
    var url by remember { mutableStateOf(userSettings.homeUrl) }
    var blacklist by remember { mutableStateOf(userSettings.websiteBlacklist) }
    var whitelist by remember { mutableStateOf(userSettings.websiteWhitelist) }
    var blockedMessage by remember { mutableStateOf(userSettings.blockedMessage) }

    var urlError by remember { mutableStateOf(false) }
    var blacklistError by remember { mutableStateOf(false) }
    var whitelistError by remember { mutableStateOf(false) }

    val saveEnabled = !urlError && !blacklistError && !whitelistError

    var showExportDialog by remember { mutableStateOf(false) }
    var exportText by remember { mutableStateOf("") }

    var showImportDialog by remember { mutableStateOf(false) }
    var importText by remember { mutableStateOf("") }
    var importError by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 60.dp, start = 16.dp, end = 16.dp, bottom = 16.dp)
    ) {
        Text(
            "Settings",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        LabelWithInfo(
            label = "Home URL",
            infoTitle = "Home URL",
            infoText = "The start page URL when the app launches."
        )
        UrlInput(
            value = url,
            onValueChange = {
                url = it
                urlError = !validateUrl(it)
            },
            isError = urlError
        )

        LabelWithInfo(
            label = "Blacklist Regex",
            infoTitle = "e.g.\n\tBlacklist (Regex)\n\t^https://.*\\.?google\\.com/.*",
            infoText = """
                Specify regular expressions (regex), one per line, to allow matching URLs.
                Escaping is required for special characters in regex like '.' and '?'.

                These patterns also use partial (contains) matching by default.

                If you need strict control, anchor your regex with '^' and '$'.

                Examples:
                - .*
                - ^https://.*\.?google\.com/.*
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
            infoTitle = "Whitelist (Regex)",
            infoText = """
                Specify regular expressions (regex), one per line, to allow matching URLs.
                Escaping is required for special characters in regex like '.' and '?'.

                These patterns also use partial (contains) matching by default.

                If you need strict control, anchor your regex with '^' and '$'.

                Examples:
                - ^https://allowedsite\.com$
                - ^https://.*\.trusted\.org/.*
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
            onValueChange = {
                blockedMessage = it
            },
            placeholder = { Text("This site is blocked by WebView Locker.") },
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
                modifier = Modifier.width(100.dp)
            ) {
                Text("Cancel")
            }

            Button(
                enabled = saveEnabled,
                modifier = Modifier.width(100.dp),
                onClick = {
                    userSettings.homeUrl = url
                    userSettings.websiteBlacklist = blacklist
                    userSettings.websiteWhitelist = whitelist
                    userSettings.blockedMessage = blockedMessage.trim()
                    onClose()
                }
            ) {
                Text("Save")
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Button(onClick = {
                val json = JSONObject().apply {
                    put("url", url)
                    put("blacklist", blacklist)
                    put("whitelist", whitelist)
                    put("blockedMessage", blockedMessage)
                }.toString()
                exportText = Base64.encodeToString(json.toByteArray(), Base64.NO_WRAP)
                showExportDialog = true
            }) {
                Text("Export")
            }

            OutlinedButton(onClick = {
                importText = ""
                importError = false
                showImportDialog = true
            }) {
                Text("Import")
            }
        }
    }

    if (showExportDialog) {
        AlertDialog(
            onDismissRequest = { showExportDialog = false },
            confirmButton = {
                TextButton(onClick = { showExportDialog = false }) {
                    Text("OK")
                }
            },
            title = { Text("Exported Settings (Base64)") },
            text = { Text(exportText, style = MaterialTheme.typography.bodySmall) }
        )
    }

    if (showImportDialog) {
        AlertDialog(
            onDismissRequest = { showImportDialog = false },
            confirmButton = {
                TextButton(onClick = {
                    try {
                        val json = JSONObject(String(Base64.decode(importText, Base64.NO_WRAP)))
                        url = json.optString("url")
                        blacklist = json.optString("blacklist")
                        whitelist = json.optString("whitelist")
                        blockedMessage = json.optString("blockedMessage")
                        importError = false
                        showImportDialog = false
                    } catch (_: Exception) {
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
                        placeholder = { Text("Paste exported Base64 string here") },
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

