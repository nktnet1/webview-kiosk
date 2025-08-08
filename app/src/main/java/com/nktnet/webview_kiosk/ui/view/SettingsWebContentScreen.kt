package com.nktnet.webview_kiosk.ui.view

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavController
import com.nktnet.webview_kiosk.config.UserSettings
import com.nktnet.webview_kiosk.ui.components.common.LabelWithInfo
import com.nktnet.webview_kiosk.ui.components.common.PatternInput
import com.nktnet.webview_kiosk.ui.components.setting.SettingLabel
import com.nktnet.webview_kiosk.ui.components.setting.SettingsActionButtons
import com.nktnet.webview_kiosk.ui.components.common.UrlInput
import com.nktnet.webview_kiosk.utils.validateMultilineRegex
import com.nktnet.webview_kiosk.utils.validateUrl

@Composable
fun SettingsWebContentScreen(
    navController: NavController,
) {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }

    var homeUrl by remember { mutableStateOf(userSettings.homeUrl) }
    var blacklist by remember { mutableStateOf(userSettings.websiteBlacklist) }
    var whitelist by remember { mutableStateOf(userSettings.websiteWhitelist) }

    var homeUrlError by remember { mutableStateOf(false) }
    var blacklistError by remember { mutableStateOf(false) }
    var whitelistError by remember { mutableStateOf(false) }

    val saveEnabled = !homeUrlError && !blacklistError && !whitelistError

    val toastRef = remember { mutableStateOf<android.widget.Toast?>(null) }

    fun showToast(message: String) {
        toastRef.value?.cancel()
        toastRef.value = android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT).also { it.show() }
    }

    fun saveSettings() {
        userSettings.homeUrl = homeUrl
        userSettings.websiteBlacklist = blacklist
        userSettings.websiteWhitelist = whitelist
        showToast("Settings saved successfully.")
    }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        SettingLabel(navController = navController, label = "Web Content")

        LabelWithInfo(
            label = "Home URL",
            infoTitle = "Home URL",
            infoText = "The URL that can be reset to using the floating icon button."
        )
        UrlInput(
            value = homeUrl,
            onValueChange = {
                homeUrl = it
                homeUrlError = !validateUrl(it)
            },
            isError = homeUrlError
        )

        LabelWithInfo(
            label = "Blacklist Regex",
            infoTitle = "Blacklist",
            infoText = """
                Specify regular expressions (regex), one per line, to allow matching URLs.
                Escaping is required for special characters in regex like '.' and '?'.

                These patterns also use partial (contains) matching by default.

                If you need strict control, anchor your regex with '^' and '$'.

                Examples:
                - .*
                - ^https://.*\\.?google\\.com/.*
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
            infoTitle = "Whitelist",
            infoText = """
                Specify regular expressions (regex), one per line, to allow matching URLs.
                Escaping is required for special characters in regex like '.' and '?'.

                These patterns also use partial (contains) matching by default.

                If you need strict control, anchor your regex with '^' and '$'.

                Examples:
                - ^https://allowedsite\\.com$
                - ^https://.*\\.trusted\\.org/.*
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
        SettingsActionButtons(
            navController = navController,
            saveEnabled = saveEnabled,
            saveSettings = { saveSettings() }
        )
    }
}
