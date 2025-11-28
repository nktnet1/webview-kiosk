package com.nktnet.webview_kiosk.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.nktnet.webview_kiosk.config.Screen
import com.nktnet.webview_kiosk.ui.components.setting.SettingLabel
import com.nktnet.webview_kiosk.ui.components.setting.SettingDivider
import com.nktnet.webview_kiosk.ui.components.setting.fielditems.device.AllowCameraSetting
import com.nktnet.webview_kiosk.ui.components.setting.fielditems.device.AllowLocationSetting
import com.nktnet.webview_kiosk.ui.components.setting.fielditems.device.AllowMicrophoneSetting
import com.nktnet.webview_kiosk.ui.components.setting.fielditems.device.BackButtonHoldActionSetting
import com.nktnet.webview_kiosk.ui.components.setting.fielditems.device.BrightnessSetting
import com.nktnet.webview_kiosk.ui.components.setting.fielditems.device.CustomAuthPasswordSetting
import com.nktnet.webview_kiosk.ui.components.setting.fielditems.device.CustomUnlockShortcutSetting
import com.nktnet.webview_kiosk.ui.components.setting.fielditems.device.DeviceRotationSetting
import com.nktnet.webview_kiosk.ui.components.setting.fielditems.device.KeepScreenOnSetting
import com.nktnet.webview_kiosk.ui.components.setting.fielditems.device.UnlockAuthRequirementSetting

@Composable
fun SettingsDeviceScreen(navController: NavController) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeContent)
            .padding(horizontal = 16.dp)
    ) {
        SettingLabel(navController = navController, label = "Device")
        SettingDivider()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            KeepScreenOnSetting()
            DeviceRotationSetting()
            BrightnessSetting()
            AllowCameraSetting()
            AllowMicrophoneSetting()
            AllowLocationSetting()
            BackButtonHoldActionSetting()
            CustomUnlockShortcutSetting()
            CustomAuthPasswordSetting()
            UnlockAuthRequirementSetting()

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = { navController.navigate(Screen.SettingsDeviceOwner.route) },
                modifier = Modifier.fillMaxWidth().height(50.dp),
            ) {
                Text("Manage Device Owner")
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
