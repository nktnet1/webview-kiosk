package com.nktnet.webview_kiosk.ui.view

import androidx.compose.foundation.layout.*
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
import com.nktnet.webview_kiosk.config.AddressBarMode
import com.nktnet.webview_kiosk.config.Theme
import com.nktnet.webview_kiosk.config.UserSettings
import com.nktnet.webview_kiosk.ui.components.LabelWithInfo
import com.nktnet.webview_kiosk.ui.components.SettingLabel
import com.nktnet.webview_kiosk.ui.components.SettingsActionButtons
import androidx.compose.runtime.MutableState

@Composable
fun SettingsAppearanceScreen(
    navController: NavController,
    themeState: MutableState<Theme>, // new param to hold theme state
    userSettings: UserSettings          // pass UserSettings to avoid recreating
) {
    val context = LocalContext.current

    var blockedMessage by remember { mutableStateOf(userSettings.blockedMessage) }
    var theme by remember { mutableStateOf(themeState.value) }  // initialize from themeState
    var addressBarMode by remember { mutableStateOf(userSettings.addressBarMode) }

    val saveEnabled = true

    val toastRef = remember { mutableStateOf<android.widget.Toast?>(null) }

    fun showToast(message: String) {
        toastRef.value?.cancel()
        toastRef.value = android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT).also { it.show() }
    }

    fun saveSettings() {
        userSettings.blockedMessage = blockedMessage.trim()
        userSettings.theme = theme
        userSettings.addressBarMode = addressBarMode
        themeState.value = theme  // update shared theme state to trigger recomposition
        showToast("Settings saved successfully.")
    }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        SettingLabel(navController = navController, label = "Appearance")

        LabelWithInfo(
            label = "Theme",
            infoTitle = "Theme",
            infoText = "Select the app theme: System default, Light, or Dark."
        )
        DropdownSelector(
            options = Theme.entries,
            selected = theme,
            onSelectedChange = { theme = it },
            modifier = Modifier.fillMaxWidth()
        ) { selected ->
            Text(
                when (selected) {
                    Theme.SYSTEM  -> "System"
                    Theme.DARK -> "Dark"
                    Theme.LIGHT -> "Light"
                },
                modifier = Modifier.padding(8.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        LabelWithInfo(
            label = "Address Bar Mode",
            infoTitle = "Address Bar",
            infoText = "Control the visibility of the browser address bar."
        )
        DropdownSelector(
            options = AddressBarMode.entries,
            selected = addressBarMode,
            onSelectedChange = { addressBarMode = it },
            modifier = Modifier.fillMaxWidth()
        ) { selected ->
            Text(
                when (selected) {
                    AddressBarMode.HIDDEN -> "Hidden"
                    AddressBarMode.HIDDEN_WHEN_LOCKED -> "Hidden when locked"
                    AddressBarMode.SHOWN -> "Shown"
                },
                modifier = Modifier.padding(8.dp)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        LabelWithInfo(
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
fun <T> DropdownSelector(
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
                        Box(Modifier.padding(horizontal = 8.dp)) {
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
