package com.nktnet.webview_locker.ui.view

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
import com.nktnet.webview_locker.auth.BiometricPromptManager
import com.nktnet.webview_locker.config.UserSettings
import com.nktnet.webview_locker.ui.components.AuthenticationErrorDisplay
import com.nktnet.webview_locker.ui.components.RequireAuthentication
import java.net.URL

@Composable
fun SettingsScreen(
    onClose: () -> Unit,
    promptManager: BiometricPromptManager
) {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }

    RequireAuthentication(
        promptManager = promptManager,
        onAuthenticated = {
            SettingsContent(userSettings = userSettings, onClose = onClose)
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
                urlError = !isValidUrl(it)
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
    }
}

@Composable
private fun LabelWithInfo(
    label: String,
    infoTitle: String,
    infoText: String
) {
    var showInfo by remember { mutableStateOf(false) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(top=16.dp, bottom=4.dp)
    ) {
        Text(
            label,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        IconButton(onClick = { showInfo = true }, modifier = Modifier.size(20.dp)) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = "$label info",
                tint = MaterialTheme.colorScheme.onSurface
            )
        }
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

@Composable
private fun UrlInput(
    value: String,
    onValueChange: (String) -> Unit,
    isError: Boolean
) {
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
    value: String,
    onValueChange: (String) -> Unit,
    isError: Boolean,
    placeholder: String,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(placeholder, fontStyle = FontStyle.Italic) },
        modifier = Modifier.fillMaxWidth(),
        isError = isError,
        minLines = 3,
    )
    if (isError) {
        Text(
            "Invalid regex pattern",
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(start = 0.dp, top = 4.dp)
        )
    }
}

private fun isValidUrl(input: String): Boolean {
    if (input.isEmpty()) {
        return true
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
