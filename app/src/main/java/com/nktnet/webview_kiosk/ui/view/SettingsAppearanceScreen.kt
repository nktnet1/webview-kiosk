package com.nktnet.webview_kiosk.ui.view

import DropdownSelector
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.nktnet.webview_kiosk.config.WebViewInset
import com.nktnet.webview_kiosk.config.option.AddressBarOption
import com.nktnet.webview_kiosk.config.option.ThemeOption
import com.nktnet.webview_kiosk.config.UserSettings
import com.nktnet.webview_kiosk.ui.components.common.LabelWithInfo
import com.nktnet.webview_kiosk.ui.components.setting.SettingLabel
import com.nktnet.webview_kiosk.ui.components.setting.SettingsActionButtons
import androidx.compose.runtime.MutableState
import com.nktnet.webview_kiosk.ui.components.setting.SettingDivider

@Composable
fun SettingsAppearanceScreen(
    navController: NavController,
    themeState: MutableState<ThemeOption>,
) {
    val context = LocalContext.current
    val userSettings = UserSettings(context)

    var blockedMessage by remember { mutableStateOf(userSettings.blockedMessage) }
    var theme by remember { mutableStateOf(themeState.value) }
    var addressBarMode by remember { mutableStateOf(userSettings.addressBarMode) }
    var webViewInset by remember { mutableStateOf(userSettings.webViewInset) }

    val saveEnabled = true

    val toastRef = remember { mutableStateOf<android.widget.Toast?>(null) }

    fun showToast(message: String) {
        toastRef.value?.cancel()
        toastRef.value = android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT).also { it.show() }
    }

    fun saveSettings() {
        userSettings.blockedMessage = blockedMessage.trim()
        userSettings.addressBarMode = addressBarMode
        userSettings.webViewInset = webViewInset

        userSettings.theme = theme
        themeState.value = theme

        showToast("Settings saved successfully.")
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .windowInsetsPadding(WindowInsets.safeContent)
            .verticalScroll(rememberScrollState()),
    ) {
        SettingLabel(navController = navController, label = "Appearance")

        SettingDivider()

        LabelWithInfo(
            modifier = Modifier.padding(bottom = 2.dp),
            label = "Theme",
            infoTitle = "Theme",
            infoText = """
                Select the app theme: System (default), Dark or Light.

                If either Dark or Light is selected, custom JavaScript will be injected to override the prefers-color-scheme property of the WebView page.
            """.trimIndent()
        )
        DropdownSelector(
            options = ThemeOption.entries,
            selected = theme,
            onSelectedChange = { theme = it },
            modifier = Modifier.fillMaxWidth()
        ) { selected ->
            Text(
                when (selected) {
                    ThemeOption.SYSTEM  -> "System"
                    ThemeOption.DARK -> "Dark"
                    ThemeOption.LIGHT -> "Light"
                },
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }

        LabelWithInfo(
            modifier = Modifier.padding(top = 16.dp, bottom = 2.dp),
            label = "Address Bar Mode",
            infoTitle = "Address Bar",
            infoText = "Control the visibility of the browser address bar."
        )
        DropdownSelector(
            options = AddressBarOption.entries,
            selected = addressBarMode,
            onSelectedChange = { addressBarMode = it },
            modifier = Modifier.fillMaxWidth()
        ) { selected ->
            Text(
                when (selected) {
                    AddressBarOption.HIDDEN -> "Hidden"
                    AddressBarOption.HIDDEN_WHEN_LOCKED -> "Hidden when locked"
                    AddressBarOption.SHOWN -> "Shown"
                },
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }

        LabelWithInfo(
            modifier = Modifier.padding(top = 16.dp, bottom = 2.dp),
            label = "WebView Insets",
            infoTitle = "Insets",
            infoText = "Select which WindowInsets the WebView should respect for padding."
        )
        DropdownSelector(
            options = WebViewInset.entries,
            selected = webViewInset,
            onSelectedChange = { webViewInset = it },
            modifier = Modifier.fillMaxWidth()
        ) { selected ->
            Text(
                selected.label,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }

        LabelWithInfo(
            modifier = Modifier.padding(top = 16.dp, bottom = 2.dp),
            label = "Blocked Message",
            infoTitle = "Blocked Message",
            infoText = "Custom message shown on blocked pages. Can be left empty."
        )
        OutlinedTextField(
            value = blockedMessage,
            onValueChange = { blockedMessage = it },
            placeholder = { Text("e.g. This site is blocked by <Company Name>") },
            isError = false,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp, max = 140.dp),
            minLines = 2,
            maxLines = 5
        )
        SettingsActionButtons(
            navController = navController,
            saveEnabled = saveEnabled,
            saveSettings = { saveSettings() }
        )
    }
}
