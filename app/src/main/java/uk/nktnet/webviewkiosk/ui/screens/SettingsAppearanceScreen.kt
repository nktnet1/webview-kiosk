package com.nktnet.webview_kiosk.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.nktnet.webview_kiosk.ui.components.setting.SettingLabel
import com.nktnet.webview_kiosk.ui.components.setting.fielditems.appearance.AddressBarModeSetting
import com.nktnet.webview_kiosk.ui.components.setting.fielditems.appearance.BlockedMessageSetting
import com.nktnet.webview_kiosk.ui.components.setting.fielditems.appearance.ThemeSetting
import com.nktnet.webview_kiosk.ui.components.setting.fielditems.appearance.WebViewInsetSetting
import com.nktnet.webview_kiosk.ui.components.setting.SettingDivider
import com.nktnet.webview_kiosk.ui.components.setting.fielditems.appearance.FloatingToolbarModeSetting
import com.nktnet.webview_kiosk.ui.components.setting.fielditems.appearance.ImmersiveModeSetting

@Composable
fun SettingsAppearanceScreen(
    navController: NavController,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeContent)
            .padding(horizontal = 16.dp)
    ) {
        SettingLabel(navController = navController, label = "Appearance")
        SettingDivider()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            ThemeSetting()
            AddressBarModeSetting()
            FloatingToolbarModeSetting()
            WebViewInsetSetting()
            ImmersiveModeSetting()
            BlockedMessageSetting()

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
