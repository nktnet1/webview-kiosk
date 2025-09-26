package com.nktnet.webview_kiosk.ui.view

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
import androidx.compose.runtime.MutableState
import com.nktnet.webview_kiosk.config.option.ThemeOption
import com.nktnet.webview_kiosk.ui.components.setting.SettingDivider

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

        SettingDivider()

        ThemeSetting(themeState)
        AddressBarModeSetting()
        WebViewInsetSetting()
        BlockedMessageSetting()
    }
}
