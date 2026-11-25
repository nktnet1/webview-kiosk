package uk.nktnet.webviewkiosk.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import uk.nktnet.webviewkiosk.R
import uk.nktnet.webviewkiosk.config.Screen
import uk.nktnet.webviewkiosk.ui.components.setting.SettingDivider
import uk.nktnet.webviewkiosk.ui.components.setting.SettingLabel
import uk.nktnet.webviewkiosk.ui.components.setting.SettingListItem
import uk.nktnet.webviewkiosk.ui.components.setting.fielditems.device.AllowCameraSetting
import uk.nktnet.webviewkiosk.ui.components.setting.fielditems.device.AllowLocationSetting
import uk.nktnet.webviewkiosk.ui.components.setting.fielditems.device.AllowMicrophoneSetting
import uk.nktnet.webviewkiosk.ui.components.setting.fielditems.device.AllowNfcSetting
import uk.nktnet.webviewkiosk.ui.components.setting.fielditems.device.AllowNotificationsSetting
import uk.nktnet.webviewkiosk.ui.components.setting.fielditems.device.BackButtonHoldActionSetting
import uk.nktnet.webviewkiosk.ui.components.setting.fielditems.device.BlockScreenCaptureSetting
import uk.nktnet.webviewkiosk.ui.components.setting.fielditems.device.BlockVolumeKeysSetting
import uk.nktnet.webviewkiosk.ui.components.setting.fielditems.device.BrightnessSetting
import uk.nktnet.webviewkiosk.ui.components.setting.fielditems.device.CustomAuthPasswordSetting
import uk.nktnet.webviewkiosk.ui.components.setting.fielditems.device.CustomUnlockShortcutSetting
import uk.nktnet.webviewkiosk.ui.components.setting.fielditems.device.DeviceRotationSetting
import uk.nktnet.webviewkiosk.ui.components.setting.fielditems.device.KeepScreenOnSetting
import uk.nktnet.webviewkiosk.ui.components.setting.fielditems.device.SettingsAuthRequirementSetting
import uk.nktnet.webviewkiosk.ui.components.setting.fielditems.device.UnlockAuthRequirementSetting
import uk.nktnet.webviewkiosk.ui.components.setting.fielditems.device.UseDirectGpsSetting

@Composable
fun SettingsDeviceScreen(navController: NavController) {
    Column(
        modifier =
            Modifier.fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(top = 4.dp)
                .padding(horizontal = 16.dp)
    ) {
        SettingLabel(
            navController = navController,
            label = stringResource(R.string.settings_device_title),
        )
        SettingDivider()

        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
        ) {
            KeepScreenOnSetting()
            DeviceRotationSetting()
            BrightnessSetting()
            AllowCameraSetting()
            AllowMicrophoneSetting()
            AllowLocationSetting()
            UseDirectGpsSetting()
            AllowNfcSetting()
            AllowNotificationsSetting()
            BackButtonHoldActionSetting()
            CustomUnlockShortcutSetting()
            CustomAuthPasswordSetting()
            UnlockAuthRequirementSetting()
            SettingsAuthRequirementSetting()
            BlockScreenCaptureSetting()
            BlockVolumeKeysSetting()

            Spacer(modifier = Modifier.height(16.dp))

            SettingListItem(
                title = stringResource(R.string.settings_device_owner_title),
                description =
                    stringResource(R.string.settings_device_owner_description),
                onClick = {
                    navController.navigate(Screen.SettingsDeviceOwner.route)
                },
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
