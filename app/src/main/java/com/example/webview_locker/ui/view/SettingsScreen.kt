package com.example.webview_locker.ui.view

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
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
import com.example.webview_locker.config.UserSettingsKeys
import androidx.core.content.edit
import com.example.webview_locker.auth.BiometricPromptManager
import java.net.URL

@Composable
fun SettingsScreen(onSave: () -> Unit, promptManager: BiometricPromptManager) {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences(UserSettingsKeys.PREFS_NAME, Context.MODE_PRIVATE)

    var url by remember { mutableStateOf(prefs.getString(UserSettingsKeys.HOME_URL, "") ?: "") }
    var blacklist by remember { mutableStateOf(prefs.getString(UserSettingsKeys.WEBSITE_BLACKLIST, "") ?: "") }
    var whitelist by remember { mutableStateOf(prefs.getString(UserSettingsKeys.WEBSITE_WHITELIST, "") ?: "") }

    var urlError by remember { mutableStateOf(false) }
    var blacklistError by remember { mutableStateOf(false) }
    var whitelistError by remember { mutableStateOf(false) }

    var showBlacklistInfo by remember { mutableStateOf(false) }
    var showWhitelistInfo by remember { mutableStateOf(false) }

    val biometricResult by promptManager.promptResults.collectAsState(initial = null)

    val enrollLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
        onResult = {
            println("Activity result: $it")
        }
    )

    LaunchedEffect(biometricResult) {
        if (biometricResult is BiometricPromptManager.BiometricResult.AuthenticationNotSet) {
            if (Build.VERSION.SDK_INT >= 30) {
                val enrollIntent = Intent(Settings.ACTION_BIOMETRIC_ENROLL).apply {
                    putExtra(
                        Settings.EXTRA_BIOMETRIC_AUTHENTICATORS_ALLOWED,
                        BIOMETRIC_STRONG or DEVICE_CREDENTIAL
                    )
                }
                enrollLauncher.launch(enrollIntent)
            }
        }
    }

    LaunchedEffect(Unit) {
        promptManager.showBiometricPrompt(
            title = "Authentication Required",
            description = "Please authenticate to modify settings"
        )
    }

    biometricResult?.let { result ->
        when (result) {
            is BiometricPromptManager.BiometricResult.AuthenticationSuccess -> {
                SettingsContent(
                    url = url,
                    onUrlChange = {
                        url = it
                        urlError = !isValidUrl(it)
                    },
                    urlError = urlError,

                    blacklist = blacklist,
                    onBlacklistChange = {
                        blacklist = it
                        blacklistError = !validateMultilineInput(it)
                    },
                    blacklistError = blacklistError,
                    showBlacklistInfo = showBlacklistInfo,
                    onShowBlacklistInfoChange = { showBlacklistInfo = it },

                    whitelist = whitelist,
                    onWhitelistChange = {
                        whitelist = it
                        whitelistError = !validateMultilineInput(it)
                    },
                    whitelistError = whitelistError,
                    showWhitelistInfo = showWhitelistInfo,
                    onShowWhitelistInfoChange = { showWhitelistInfo = it },

                    onSave = {
                        prefs.edit {
                            putString(UserSettingsKeys.HOME_URL, url)
                            putString(UserSettingsKeys.WEBSITE_BLACKLIST, blacklist)
                            putString(UserSettingsKeys.WEBSITE_WHITELIST, whitelist)
                        }
                        onSave()
                    },
                    saveEnabled = !urlError && !blacklistError && !whitelistError
                )
            }

            else -> AuthenticationErrorScreen(
                errorResult = result,
                onRetry = {
                    promptManager.showBiometricPrompt(
                        title = "Authentication Required",
                        description = "Please authenticate to modify settings"
                    )
                }
            )
        }
    }
}

@Composable
private fun SettingsContent(
    url: String,
    onUrlChange: (String) -> Unit,
    urlError: Boolean,

    blacklist: String,
    onBlacklistChange: (String) -> Unit,
    blacklistError: Boolean,
    showBlacklistInfo: Boolean,
    onShowBlacklistInfoChange: (Boolean) -> Unit,

    whitelist: String,
    onWhitelistChange: (String) -> Unit,
    whitelistError: Boolean,
    showWhitelistInfo: Boolean,
    onShowWhitelistInfoChange: (Boolean) -> Unit,

    onSave: () -> Unit,
    saveEnabled: Boolean,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 24.dp, start = 16.dp, end = 16.dp, bottom = 16.dp)
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineSmall)

        Spacer(modifier = Modifier.height(16.dp))

        UrlInput(url, onUrlChange, urlError)

        Spacer(modifier = Modifier.height(24.dp))

        BlacklistInput(
            blacklist,
            onBlacklistChange,
            blacklistError,
            onShowBlacklistInfoChange
        )

        Spacer(modifier = Modifier.height(16.dp))

        WhitelistInput(
            whitelist,
            onWhitelistChange,
            whitelistError,
            onShowWhitelistInfoChange
        )

        Spacer(modifier = Modifier.height(24.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            Button(
                enabled = saveEnabled,
                onClick = onSave
            ) {
                Text("Save")
            }
        }
    }

    if (showBlacklistInfo) BlacklistInfoDialog(onDismiss = { onShowBlacklistInfoChange(false) })

    if (showWhitelistInfo) WhitelistInfoDialog(onDismiss = { onShowWhitelistInfoChange(false) })
}

@Composable
private fun UrlInput(value: String, onValueChange: (String) -> Unit, isError: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Home URL", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.width(4.dp))
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
private fun BlacklistInput(
    value: String,
    onValueChange: (String) -> Unit,
    isError: Boolean,
    onShowInfoChange: (Boolean) -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Blacklist", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.width(4.dp))
        IconButton(onClick = { onShowInfoChange(true) }, modifier = Modifier.size(20.dp)) {
            Icon(Icons.Default.Info, contentDescription = "Blacklist info")
        }
    }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text("e.g.\n*", fontStyle = FontStyle.Italic) },
        modifier = Modifier.fillMaxWidth(),
        isError = isError,
        minLines = 3
    )
    if (isError) {
        Text(
            "Invalid blacklist format",
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun WhitelistInput(
    value: String,
    onValueChange: (String) -> Unit,
    isError: Boolean,
    onShowInfoChange: (Boolean) -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Whitelist", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.width(4.dp))
        IconButton(onClick = { onShowInfoChange(true) }, modifier = Modifier.size(20.dp)) {
            Icon(Icons.Default.Info, contentDescription = "Whitelist info")
        }
    }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text("e.g.\nhttps://homepage.com\nhttps://*.company.com/*") },
        modifier = Modifier.fillMaxWidth(),
        isError = isError,
        minLines = 3
    )
    if (isError) {
        Text(
            "Invalid whitelist format",
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun BlacklistInfoDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("OK")
            }
        },
        title = { Text("Blacklist: sites to block") },
        text = {
            Text(
                "Specify blocked URL patterns, one per line."
                        + "\n\nPatterns follow the Chromium URL blocklist filter format:"
                        + "    https://www.chromium.org/administrators/url-blocklist-filter-format/"
                        + "\n\nYou can also block all (*) and utilise the whitelist to allow specific sites."
            )
        }
    )
}

@Composable
private fun WhitelistInfoDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("OK")
            }
        },
        title = { Text("Whitelist: sites to bypass blacklist") },
        text = {
            Text(
                "Specify allowed URL patterns, one per line."
                        + "\n\nPatterns follow the Chromium URL blocklist filter format:"
                        + "    https://www.chromium.org/administrators/url-blocklist-filter-format/"
                        + "\n\nUse this list to unblock any sites blocked by the blacklist"
            )
        }
    )
}

@Composable
private fun AuthenticationErrorScreen(
    errorResult: BiometricPromptManager.BiometricResult?,
    onRetry: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = when (errorResult) {
                is BiometricPromptManager.BiometricResult.AuthenticationError ->
                    "Authentication Error: ${errorResult.error}"
                BiometricPromptManager.BiometricResult.HardwareUnavailable ->
                    "Biometric hardware unavailable"
                BiometricPromptManager.BiometricResult.FeatureUnavailable ->
                    "Biometric feature not available"
                BiometricPromptManager.BiometricResult.AuthenticationNotSet ->
                    "No biometric or credentials enrolled"
                BiometricPromptManager.BiometricResult.AuthenticationFailed ->
                    "Authentication failed"
                else -> "Authentication failed"
            },
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        Button(onClick = onRetry) {
            Text("Retry Authentication")
        }
    }
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
