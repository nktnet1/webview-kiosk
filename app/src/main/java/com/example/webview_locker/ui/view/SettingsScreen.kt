package com.example.webview_locker.ui.view

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import com.example.webview_locker.auth.BiometricPromptManager
import com.example.webview_locker.config.UserSettingsKeys
import com.example.webview_locker.ui.components.AuthenticationErrorDisplay
import com.example.webview_locker.ui.components.RequireAuthentication
import java.net.URL

@Composable
fun SettingsScreen(
    onSave: () -> Unit,
    promptManager: BiometricPromptManager
) {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences(UserSettingsKeys.PREFS_NAME, Context.MODE_PRIVATE)

    RequireAuthentication(
        promptManager = promptManager,
        onAuthenticated = {
            SettingsContent(prefs = prefs, onSave = onSave)
        },
        onFailed = { errorResult ->
            AuthenticationErrorDisplay(errorResult = errorResult) {
                promptManager.showBiometricPrompt(
                    title = "Authentication Required",
                    description = "Please authenticate to modify settings"
                )
            }
        }
    )
}

@Composable
private fun SettingsContent(
    prefs: android.content.SharedPreferences,
    onSave: () -> Unit,
) {
    var url by remember { mutableStateOf(prefs.getString(UserSettingsKeys.HOME_URL, "") ?: "") }
    var blacklist by remember { mutableStateOf(prefs.getString(UserSettingsKeys.WEBSITE_BLACKLIST, "") ?: "") }
    var whitelist by remember { mutableStateOf(prefs.getString(UserSettingsKeys.WEBSITE_WHITELIST, "") ?: "") }

    var urlError by remember { mutableStateOf(false) }
    var blacklistError by remember { mutableStateOf(false) }
    var whitelistError by remember { mutableStateOf(false) }

    var showBlacklistInfo by remember { mutableStateOf(false) }
    var showWhitelistInfo by remember { mutableStateOf(false) }

    val saveEnabled = !urlError && !blacklistError && !whitelistError

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineSmall)

        Spacer(Modifier.height(16.dp))

        UrlInput(
            value = url,
            onValueChange = {
                url = it
                urlError = !isValidUrl(it)
            },
            isError = urlError
        )

        Spacer(Modifier.height(24.dp))

        PatternInput(
            label = "Blacklist",
            value = blacklist,
            onValueChange = {
                blacklist = it
                blacklistError = !validateMultilineInput(it)
            },
            isError = blacklistError,
            showInfo = showBlacklistInfo,
            onShowInfoChange = { showBlacklistInfo = it },
            placeholder = "e.g.\n*",
            infoTitle = "Blacklist: sites to block",
            infoText = """
                Specify blocked URL patterns, one per line.

                Patterns follow the Chromium URL blocklist filter format:
                https://www.chromium.org/administrators/url-blocklist-filter-format/

                You can also block all (*) and use the whitelist to allow specific sites.
            """.trimIndent()
        )

        Spacer(Modifier.height(16.dp))

        PatternInput(
            label = "Whitelist",
            value = whitelist,
            onValueChange = {
                whitelist = it
                whitelistError = !validateMultilineInput(it)
            },
            isError = whitelistError,
            showInfo = showWhitelistInfo,
            onShowInfoChange = { showWhitelistInfo = it },
            placeholder = "e.g.\nhttps://homepage.com\nhttps://*.company.com/*",
            infoTitle = "Whitelist: sites to bypass blacklist",
            infoText = """
                Specify allowed URL patterns, one per line.

                Patterns follow the Chromium URL blocklist filter format:
                https://www.chromium.org/administrators/url-blocklist-filter-format/

                Use this list to unblock any sites blocked by the blacklist.
            """.trimIndent()
        )

        Spacer(Modifier.height(24.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            Button(
                enabled = saveEnabled,
                onClick = {
                    prefs.edit {
                        putString(UserSettingsKeys.HOME_URL, url)
                        putString(UserSettingsKeys.WEBSITE_BLACKLIST, blacklist)
                        putString(UserSettingsKeys.WEBSITE_WHITELIST, whitelist)
                    }
                    onSave()
                }
            ) {
                Text("Save")
            }
        }
    }

    if (showBlacklistInfo) {
        InfoDialog(
            title = "Blacklist: sites to block",
            text = """
                Specify blocked URL patterns, one per line.

                Patterns follow the Chromium URL blocklist filter format:
                https://www.chromium.org/administrators/url-blocklist-filter-format/

                You can also block all (*) and use the whitelist to allow specific sites.
            """.trimIndent(),
            onDismiss = { showBlacklistInfo = false }
        )
    }

    if (showWhitelistInfo) {
        InfoDialog(
            title = "Whitelist: sites to bypass blacklist",
            text = """
                Specify allowed URL patterns, one per line.

                Patterns follow the Chromium URL blocklist filter format:
                https://www.chromium.org/administrators/url-blocklist-filter-format/

                Use this list to unblock any sites blocked by the blacklist.
            """.trimIndent(),
            onDismiss = { showWhitelistInfo = false }
        )
    }
}

@Composable
private fun UrlInput(
    value: String,
    onValueChange: (String) -> Unit,
    isError: Boolean
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Home URL", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.width(4.dp))
    }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text("e.g. https://google.com.au", fontStyle = FontStyle.Italic) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        isError = isError
    )
    if (isError) {
        Text(
            "Must start with http:// or https:// and be a valid URL with a proper domain",
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(start = 16.dp, top = 4.dp)
        )
    }
}

@Composable
private fun PatternInput(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    isError: Boolean,
    showInfo: Boolean,
    onShowInfoChange: (Boolean) -> Unit,
    placeholder: String,
    infoTitle: String,
    infoText: String
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.width(4.dp))
        IconButton(onClick = { onShowInfoChange(true) }, modifier = Modifier.size(20.dp)) {
            Icon(Icons.Default.Info, contentDescription = "$label info")
        }
    }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(placeholder, fontStyle = FontStyle.Italic) },
        modifier = Modifier.fillMaxWidth(),
        isError = isError,
        minLines = 3
    )
    if (isError) {
        Text(
            "Invalid $label format",
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(start = 0.dp, top = 4.dp)
        )
    }

    if (showInfo) {
        InfoDialog(title = infoTitle, text = infoText, onDismiss = { onShowInfoChange(false) })
    }
}

@Composable
private fun InfoDialog(
    title: String,
    text: String,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("OK")
            }
        },
        title = { Text(title) },
        text = { Text(text) }
    )
}

// Validation helpers

private fun isValidUrl(input: String): Boolean {
    if (!(input.startsWith("http://") || input.startsWith("https://"))) return false
    return try {
        val inputUrl = URL(input)
        inputUrl.host.contains(".") && (inputUrl.protocol == "http" || inputUrl.protocol == "https")
    } catch (_: Exception) {
        false
    }
}

private fun isValidPatternLine(line: String): Boolean {
    val trimmed = line.trim()
    if (trimmed.isEmpty()) return true
    if (trimmed == "*") return true   // allow block all wildcard
    return trimmed.startsWith("http://") ||
            trimmed.startsWith("https://") ||
            trimmed.startsWith("*.") ||
            trimmed.startsWith("[") ||  // IP range in brackets
            trimmed.startsWith("file://")
}

private fun validateMultilineInput(text: String): Boolean {
    return text.lines().all { isValidPatternLine(it) }
}
