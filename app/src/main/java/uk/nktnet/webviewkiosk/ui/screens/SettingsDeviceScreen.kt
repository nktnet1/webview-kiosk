package uk.nktnet.webviewkiosk.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import uk.nktnet.webviewkiosk.config.option.DeviceRotationOption
import uk.nktnet.webviewkiosk.ui.components.setting.SettingLabel
import uk.nktnet.webviewkiosk.ui.components.setting.SettingDivider
import uk.nktnet.webviewkiosk.ui.components.setting.fielditems.device.AllowCameraSetting
import uk.nktnet.webviewkiosk.ui.components.setting.fielditems.device.AllowLocationSetting
import uk.nktnet.webviewkiosk.ui.components.setting.fielditems.device.AllowMicrophoneSetting
import uk.nktnet.webviewkiosk.ui.components.setting.fielditems.device.CustomUnlockShortcutSetting
import uk.nktnet.webviewkiosk.ui.components.setting.fielditems.device.DeviceRotationSetting
import uk.nktnet.webviewkiosk.ui.components.setting.fielditems.device.KeepScreenOnSetting

@Composable
fun SettingsDeviceScreen(
    navController: NavController,
    keepScreenOnState: MutableState<Boolean>,
    deviceRotationState: MutableState<DeviceRotationOption>
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .windowInsetsPadding(WindowInsets.safeContent)
            .verticalScroll(rememberScrollState())
    ) {
        SettingLabel(navController = navController, label = "Device")

        SettingDivider()

        KeepScreenOnSetting(keepScreenOnState)
        DeviceRotationSetting(deviceRotationState)
        AllowCameraSetting()
        AllowMicrophoneSetting()
        AllowLocationSetting()
        CustomUnlockShortcutSetting()
    }
}
