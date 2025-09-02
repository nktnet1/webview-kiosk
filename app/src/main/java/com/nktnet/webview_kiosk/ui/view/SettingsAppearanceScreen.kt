package com.nktnet.webview_kiosk.ui.view

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.nktnet.webview_kiosk.ui.components.setting.SettingLabel
import com.nktnet.webview_kiosk.ui.components.setting.fields.appearance.AddressBarModeSetting
import com.nktnet.webview_kiosk.ui.components.setting.fields.appearance.BlockedMessageSetting
import com.nktnet.webview_kiosk.ui.components.setting.fields.appearance.ThemeSetting
import com.nktnet.webview_kiosk.ui.components.setting.fields.appearance.WebViewInsetSetting
import androidx.compose.runtime.MutableState
import com.nktnet.webview_kiosk.config.option.ThemeOption

@Composable
fun SettingsAppearanceScreen(
    navController: NavController,
    themeState: MutableState<ThemeOption>,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .windowInsetsPadding(WindowInsets.safeContent)
            .verticalScroll(rememberScrollState())
    ) {
        SettingLabel(navController = navController, label = "Appearance")
        Spacer(Modifier.height(16.dp))

        ThemeSetting(themeState)
        AddressBarModeSetting()
        WebViewInsetSetting()
        BlockedMessageSetting()
    }
}
