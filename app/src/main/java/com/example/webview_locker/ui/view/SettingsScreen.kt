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
import androidx.compose.ui.unit.dp
import com.example.webview_locker.config.UserSettingsKeys
import androidx.core.content.edit
import java.net.URL

@Composable
fun SettingsScreen(onSave: () -> Unit) {
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

    fun isValidUrl(input: String): Boolean {
        if (!(input.startsWith("http://") || input.startsWith("https://"))) return false
        return try {
            val inputUrl = URL(input)
            inputUrl.host.contains(".") && (inputUrl.protocol == "http" || inputUrl.protocol == "https")
        } catch (e: Exception) {
            false
        }
    }

    fun isValidPatternLine(line: String): Boolean {
        val trimmed = line.trim()
        if (trimmed.isEmpty()) return true
        if (trimmed == "*") return true   // allow block all wildcard
        return trimmed.startsWith("http://") ||
                trimmed.startsWith("https://") ||
                trimmed.startsWith("*.") ||
                trimmed.startsWith("[") ||  // IP range in brackets
                trimmed.startsWith("file://")
    }


    fun validateMultilineInput(text: String): Boolean {
        return text.lines().all { isValidPatternLine(it) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text("Configure Start URL", style = MaterialTheme.typography.headlineSmall)

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = url,
            onValueChange = {
                url = it
                urlError = !isValidUrl(it)
            },
            label = { Text("Home URL") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            isError = urlError
        )
        if (urlError) {
            Text(
                "Must start with http:// or https:// and be a valid URL with a proper domain",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(start = 16.dp, top = 4.dp)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Blacklist", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.width(4.dp))
            IconButton(onClick = { showBlacklistInfo = true }, modifier = Modifier.size(20.dp)) {
                Icon(Icons.Default.Info, contentDescription = "Blacklist info")
            }
        }
        OutlinedTextField(
            value = blacklist,
            onValueChange = {
                blacklist = it
                blacklistError = !validateMultilineInput(it)
            },
            placeholder = { Text("e.g.\n*") },
            modifier = Modifier.fillMaxWidth(),
            isError = blacklistError,
            minLines = 3
        )
        if (blacklistError) {
            Text(
                "Invalid blacklist format",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Whitelist", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.width(4.dp))
            IconButton(onClick = { showWhitelistInfo = true }, modifier = Modifier.size(20.dp)) {
                Icon(Icons.Default.Info, contentDescription = "Whitelist info")
            }
        }
        OutlinedTextField(
            value = whitelist,
            onValueChange = {
                whitelist = it
                whitelistError = !validateMultilineInput(it)
            },
            placeholder = { Text("e.g.\nhttps://homepage.com\nhttps://*.company.com/*") },
            modifier = Modifier.fillMaxWidth(),
            isError = whitelistError,
            minLines = 3
        )
        if (whitelistError) {
            Text(
                "Invalid whitelist format",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            enabled = !urlError && !blacklistError && !whitelistError,
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

    if (showBlacklistInfo) {
        AlertDialog(
            onDismissRequest = { showBlacklistInfo = false },
            confirmButton = {
                TextButton(onClick = { showBlacklistInfo = false }) {
                    Text("OK")
                }
            },
            title = { Text("Blacklist Info") },
            text = {
                Text(
                    "Specify blocked URL patterns, one per line."
                            + "\n\nPatterns follow the Chromium URL blocklist filter format:"
                            + "    https://www.chromium.org/administrators/url-blocklist-filter-format/"
                            + "\n\nYou can also block all (*) and utilise the whitelist allow specific sites."
                )
            }
        )
    }

    if (showWhitelistInfo) {
        AlertDialog(
            onDismissRequest = { showWhitelistInfo = false },
            confirmButton = {
                TextButton(onClick = { showWhitelistInfo = false }) {
                    Text("OK")
                }
            },
            title = { Text("Whitelist Info") },
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
}
