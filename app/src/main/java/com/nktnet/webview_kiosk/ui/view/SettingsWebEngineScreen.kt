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
import androidx.compose.ui.Alignment
import androidx.navigation.NavController
import android.webkit.WebSettings
import com.nktnet.webview_kiosk.config.UserSettings
import com.nktnet.webview_kiosk.ui.components.common.LabelWithInfo
import com.nktnet.webview_kiosk.ui.components.setting.SettingLabel
import com.nktnet.webview_kiosk.ui.components.setting.SettingsActionButtons
import com.nktnet.webview_kiosk.ui.components.setting.SettingDivider

@Composable
fun SettingsWebEngineScreen(
    navController: NavController,
) {
    val context = LocalContext.current
    val userSettings = UserSettings(context)

    var enableJavaScript by remember { mutableStateOf(userSettings.enableJavaScript) }
    var enableDomStorage by remember { mutableStateOf(userSettings.enableDomStorage) }
    var acceptCookies by remember { mutableStateOf(userSettings.acceptCookies) }
    var acceptThirdPartyCookies by remember { mutableStateOf(userSettings.acceptThirdPartyCookies) }
    var cacheMode by remember { mutableIntStateOf(userSettings.cacheMode) }

    val toastRef = remember { mutableStateOf<android.widget.Toast?>(null) }

    fun showToast(message: String) {
        toastRef.value?.cancel()
        toastRef.value = android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT).also { it.show() }
    }

    fun saveSettings() {
        userSettings.enableJavaScript = enableJavaScript
        userSettings.enableDomStorage = enableDomStorage
        userSettings.acceptCookies = acceptCookies
        userSettings.acceptThirdPartyCookies = acceptThirdPartyCookies
        userSettings.cacheMode = cacheMode
        showToast("Settings saved successfully.")
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .windowInsetsPadding(WindowInsets.safeContent)
            .verticalScroll(rememberScrollState()),
    ) {
        SettingLabel(navController = navController, label = "Web Engine")

        SettingDivider()

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            LabelWithInfo(
                label = "Enable JavaScript",
                infoTitle = "JavaScript",
                infoText = "Allow the execution of JavaScript in web pages."
            )
            Switch(
                checked = enableJavaScript,
                onCheckedChange = { enableJavaScript = it }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            LabelWithInfo(
                label = "Enable DOM Storage",
                infoTitle = "DOM Storage",
                infoText = "Allow web pages to use DOM storage APIs like local storage and session storage."
            )
            Switch(
                checked = enableDomStorage,
                onCheckedChange = { enableDomStorage = it }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            LabelWithInfo(
                label = "Accept Cookies",
                infoTitle = "Cookies",
                infoText = "Allow websites to store and read cookies."
            )
            Switch(
                checked = acceptCookies,
                onCheckedChange = { acceptCookies = it }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            LabelWithInfo(
                label = "Accept Third-party Cookies",
                infoTitle = "Third-party Cookies",
                infoText = "Allow third-party websites to set cookies in this WebView."
            )
            Switch(
                checked = acceptThirdPartyCookies,
                onCheckedChange = { acceptThirdPartyCookies = it }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        LabelWithInfo(
            label = "Cache Mode",
            infoTitle = "Cache Mode",
            infoText = "Control how the WebView uses its cache when loading pages."
        )
        DropdownSelector(
            options = listOf(
                WebSettings.LOAD_DEFAULT,
                WebSettings.LOAD_CACHE_ELSE_NETWORK,
                WebSettings.LOAD_NO_CACHE,
                WebSettings.LOAD_CACHE_ONLY
            ),
            selected = cacheMode,
            onSelectedChange = { cacheMode = it },
            modifier = Modifier.fillMaxWidth()
        ) { selected ->
            Text(
                when (selected) {
                    WebSettings.LOAD_DEFAULT -> "Default"
                    WebSettings.LOAD_CACHE_ELSE_NETWORK -> "Cache else network"
                    WebSettings.LOAD_NO_CACHE -> "No cache"
                    WebSettings.LOAD_CACHE_ONLY -> "Cache only"
                    else -> "Unknown"
                },
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        SettingsActionButtons(
            navController = navController,
            saveEnabled = true,
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
