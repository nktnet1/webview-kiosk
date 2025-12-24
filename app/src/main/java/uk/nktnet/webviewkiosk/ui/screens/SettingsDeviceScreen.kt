package com.nktnet.webview_kiosk.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.nktnet.webview_kiosk.R
import com.nktnet.webview_kiosk.config.Screen
import com.nktnet.webview_kiosk.ui.components.setting.SettingLabel
import com.nktnet.webview_kiosk.ui.components.setting.SettingDivider
import com.nktnet.webview_kiosk.ui.components.setting.fielditems.device.AllowCameraSetting
import com.nktnet.webview_kiosk.ui.components.setting.fielditems.device.AllowLocationSetting
import com.nktnet.webview_kiosk.ui.components.setting.fielditems.device.AllowMicrophoneSetting
import com.nktnet.webview_kiosk.ui.components.setting.fielditems.device.AllowNotificationsSetting
import com.nktnet.webview_kiosk.ui.components.setting.fielditems.device.BackButtonHoldActionSetting
import com.nktnet.webview_kiosk.ui.components.setting.fielditems.device.BlockVolumeKeysSetting
import com.nktnet.webview_kiosk.ui.components.setting.fielditems.device.BrightnessSetting
import com.nktnet.webview_kiosk.ui.components.setting.fielditems.device.CustomAuthPasswordSetting
import com.nktnet.webview_kiosk.ui.components.setting.fielditems.device.CustomUnlockShortcutSetting
import com.nktnet.webview_kiosk.ui.components.setting.fielditems.device.DeviceRotationSetting
import com.nktnet.webview_kiosk.ui.components.setting.fielditems.device.KeepScreenOnSetting
import com.nktnet.webview_kiosk.ui.components.setting.fielditems.device.BlockScreenCaptureSetting
import com.nktnet.webview_kiosk.ui.components.setting.fielditems.device.UnlockAuthRequirementSetting

@Composable
fun SettingsDeviceScreen(navController: NavController) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(top = 4.dp)
            .padding(horizontal = 16.dp),
    ) {
        SettingLabel(
            navController = navController,
            label = stringResource(R.string.settings_device_title)
        )
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
            AllowNotificationsSetting()
            BackButtonHoldActionSetting()
            CustomUnlockShortcutSetting()
            CustomAuthPasswordSetting()
            UnlockAuthRequirementSetting()
            BlockScreenCaptureSetting()
            BlockVolumeKeysSetting()

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = { navController.navigate(Screen.SettingsDeviceOwner.route) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Manage Device Owner")
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
