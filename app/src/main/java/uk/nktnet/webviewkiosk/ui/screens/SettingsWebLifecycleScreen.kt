package com.nktnet.webview_kiosk.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.nktnet.webview_kiosk.R
import com.nktnet.webview_kiosk.ui.components.setting.SettingDivider
import com.nktnet.webview_kiosk.ui.components.setting.SettingLabel
import com.nktnet.webview_kiosk.ui.components.setting.fielditems.weblifecycle.DimScreenOnInactivitySecondsSetting
import com.nktnet.webview_kiosk.ui.components.setting.fielditems.weblifecycle.LockOnLaunchSetting
import com.nktnet.webview_kiosk.ui.components.setting.fielditems.weblifecycle.RefreshOnLoadingErrorIntervalSecondsSetting
import com.nktnet.webview_kiosk.ui.components.setting.fielditems.weblifecycle.ResetOnInactivitySecondsSetting
import com.nktnet.webview_kiosk.ui.components.setting.fielditems.weblifecycle.ResetOnLaunchSetting

@Composable
fun SettingsWebLifecycleScreen(navController: NavController) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(top = 4.dp)
            .padding(horizontal = 16.dp),
    ) {
        SettingLabel(
            navController = navController,
            label = stringResource(R.string.settings_web_lifecycle_title)
        )
        SettingDivider()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            LockOnLaunchSetting()
            ResetOnLaunchSetting()
            ResetOnInactivitySecondsSetting()
            DimScreenOnInactivitySecondsSetting()
            RefreshOnLoadingErrorIntervalSecondsSetting()
        }
    }
}
