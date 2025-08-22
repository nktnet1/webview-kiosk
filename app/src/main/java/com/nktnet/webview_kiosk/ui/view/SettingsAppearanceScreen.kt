package com.nktnet.webview_kiosk.ui.view

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.graphics.Shape
import androidx.navigation.NavController
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

    val saveEnabled = true

    val toastRef = remember { mutableStateOf<android.widget.Toast?>(null) }

    fun showToast(message: String) {
        toastRef.value?.cancel()
        toastRef.value = android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT).also { it.show() }
    }

    fun saveSettings() {
        userSettings.blockedMessage = blockedMessage.trim()
        userSettings.addressBarMode = addressBarMode

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
            modifier = Modifier.padding(bottom=4.dp),
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

        Spacer(modifier = Modifier.height(16.dp))

        LabelWithInfo(
            modifier = Modifier.padding(top=16.dp, bottom=4.dp),
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

        Spacer(modifier = Modifier.height(24.dp))

        LabelWithInfo(
            modifier = Modifier.padding(top=16.dp, bottom=4.dp),
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

@Composable
private fun <T> DropdownSelector(
    options: List<T>,
    selected: T,
    onSelectedChange: (T) -> Unit,
    modifier: Modifier = Modifier,
    itemContent: @Composable (T) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var buttonWidth by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val shape: Shape = MaterialTheme.shapes.extraSmall

    Box(modifier = modifier) {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier
                .fillMaxWidth()
                .onSizeChanged { buttonWidth = it.width },
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
            shape = shape
        ) {
            Box(Modifier.weight(1f)) {
                itemContent(selected)
            }
            Icon(Icons.Default.ArrowDropDown, contentDescription = "Select")
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.width(with(density) { buttonWidth.toDp() })
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Box(Modifier) {
                            itemContent(option)
                        }
                    },
                    onClick = {
                        onSelectedChange(option)
                        expanded = false
                    }
                )
            }
        }
    }
}
