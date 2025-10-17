package com.nktnet.webview_kiosk.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.nktnet.webview_kiosk.ui.components.setting.SettingDivider
import com.nktnet.webview_kiosk.ui.components.setting.SettingLabel
import com.nktnet.webview_kiosk.ui.components.setting.fielditems.jsscript.ApplyAppThemeSetting
import com.nktnet.webview_kiosk.ui.components.setting.fielditems.jsscript.ApplyDesktopViewportWidthSetting
import com.nktnet.webview_kiosk.ui.components.setting.fielditems.jsscript.CustomScriptOnPageFinishSetting
import com.nktnet.webview_kiosk.ui.components.setting.fielditems.jsscript.CustomScriptOnPageStartSetting

@Composable
fun SettingsJsScriptsScreen(
    navController: NavController,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .windowInsetsPadding(WindowInsets.safeContent)
            .verticalScroll(rememberScrollState()),
    ) {
        SettingLabel(navController = navController, label = "JS Scripts")

        SettingDivider()

        ApplyAppThemeSetting()
        ApplyDesktopViewportWidthSetting()
        CustomScriptOnPageStartSetting()
        CustomScriptOnPageFinishSetting()
    }
}
