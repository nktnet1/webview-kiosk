package com.nktnet.webview_kiosk.ui.view

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.nktnet.webview_kiosk.config.UserSettings
import com.nktnet.webview_kiosk.ui.components.common.LabelWithInfo
import com.nktnet.webview_kiosk.ui.components.setting.SettingLabel
import com.nktnet.webview_kiosk.ui.components.setting.SettingsActionButtons

@Composable
fun SettingsDeviceScreen(
    navController: NavController,
    keepScreenOnState: MutableState<Boolean>,
) {
    val context = LocalContext.current
    val userSettings = UserSettings(context)

    var keepScreenOn by remember { mutableStateOf(keepScreenOnState) }
    val saveEnabled = true

    val toastRef = remember { mutableStateOf<android.widget.Toast?>(null) }

    fun showToast(message: String) {
        toastRef.value?.cancel()
        toastRef.value = android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT).also { it.show() }
    }

    fun saveSettings() {
        userSettings.keepScreenOn = keepScreenOn.value
        keepScreenOnState.value = keepScreenOn.value
        showToast("Settings saved successfully.")
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeContent)
            .padding(horizontal = 16.dp)
    ) {
        SettingLabel(navController = navController, label = "Device")

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            LabelWithInfo(
                label = "Keep Screen On",
                infoTitle = "Keep Screen On",
                infoText = "Enable this option to keep your device awake (no screen timeout)"
            )
            Switch(
                checked = keepScreenOn.value,
                onCheckedChange = { keepScreenOn.value = it }
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        SettingsActionButtons(
            navController = navController,
            saveEnabled = saveEnabled,
            saveSettings = { saveSettings() }
        )
    }
}
