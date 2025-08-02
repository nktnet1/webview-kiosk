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
import com.example.webview_locker.auth.BiometricPromptManager
import com.example.webview_locker.config.UserSettings
import com.example.webview_locker.ui.components.AuthenticationErrorDisplay
import com.example.webview_locker.ui.components.RequireAuthentication
import java.net.URL

@Composable
fun SettingsScreen(
    onSave: () -> Unit,
    promptManager: BiometricPromptManager
) {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }

    RequireAuthentication(
        promptManager = promptManager,
        onAuthenticated = {
            SettingsContent(userSettings = userSettings, onSave = onSave)
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
    userSettings: UserSettings,
    onSave: () -> Unit,
) {
    var url by remember { mutableStateOf(userSettings.homeUrl) }
    var blacklist by remember { mutableStateOf(userSettings.websiteBlacklist) }
    var whitelist by remember { mutableStateOf(userSettings.websiteWhitelist) }

    var urlError by remember { mutableStateOf(false) }
    var blacklistError by remember { mutableStateOf(false) }
    var whitelistError by remember { mutableStateOf(false) }

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
            label = "Blacklist Regex",
            value = blacklist,
            onValueChange = {
                blacklist = it
                blacklistError = !validateMultilineRegex(it)
            },
            isError = blacklistError,
            placeholder = "^https://.*\\.example\\.com/.*\n^https://blockedsite\\.com/.*",
            infoTitle = "Blacklist (Regex)",
            infoText = """
        Specify regular expressions, one per line, to block matching URLs.

        Regular expressions use partial (contains) matching. For example,
        "example.com" will match any URL that contains it, such as 
        "https://example.com/page" or "https://sub.example.com".

        To restrict more precisely, use anchors like ^ and $:
        - ^https://example\.com$ matches only the exact URL.
        - ^https://.*\.example\.com/.* matches all subdomains and paths.

        Special characters like '.' and '?' must be escaped with '\\'.

        Whitelist patterns (if matched) will override blacklist patterns.
            """.trimIndent()
        )

        Spacer(Modifier.height(16.dp))

        PatternInput(
            label = "Whitelist Regex",
            value = whitelist,
            onValueChange = {
                whitelist = it
                whitelistError = !validateMultilineRegex(it)
            },
            isError = whitelistError,
            placeholder = "^https://allowedsite\\.com/.*\n^https://.*\\.trusted\\.org/.*",
            infoTitle = "Whitelist (Regex)",
            infoText = """
        Specify regular expressions, one per line, to allow matching URLs.

        These patterns also use partial (contains) matching by default.

        If you need strict control, anchor your regex:
        - ^https://allowedsite\.com$ for exact match
        - ^https://.*\.trusted\.org/.* for subdomains

        Escaping is required for special characters like '.' and '?'.

        Whitelist patterns take precedence over blacklist patterns.
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
                    userSettings.homeUrl = url
                    userSettings.websiteBlacklist = blacklist
                    userSettings.websiteWhitelist = whitelist
                    onSave()
                }
            ) {
                Text("Save")
            }
        }
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
    placeholder: String,
    infoTitle: String,
    infoText: String
) {
    var showInfo by remember { mutableStateOf(false) }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.width(4.dp))
        IconButton(onClick = { showInfo = true }, modifier = Modifier.size(20.dp)) {
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
            "Invalid regex pattern",
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(start = 0.dp, top = 4.dp)
        )
    }

    if (showInfo) {
        AlertDialog(
            onDismissRequest = { showInfo = false },
            confirmButton = {
                TextButton(onClick = { showInfo = false }) {
                    Text("OK")
                }
            },
            title = { Text(infoTitle) },
            text = { Text(infoText) }
        )
    }
}

private fun isValidUrl(input: String): Boolean {
    if (input.isEmpty()) {
        return true;
    }
    if (!(input.startsWith("http://") || input.startsWith("https://"))) {
        return false
    }
    return try {
        val inputUrl = URL(input)
        inputUrl.host.contains(".") && (inputUrl.protocol == "http" || inputUrl.protocol == "https")
    } catch (_: Exception) {
        false
    }
}

private fun validateMultilineRegex(text: String): Boolean {
    return text.lines().all { line ->
        val trimmed = line.trim()
        if (trimmed.isEmpty()) return@all true
        try {
            Regex(trimmed)
            true
        } catch (_: Exception) {
            false
        }
    }
}
