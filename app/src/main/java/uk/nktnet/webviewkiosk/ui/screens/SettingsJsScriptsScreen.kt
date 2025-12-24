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
import com.nktnet.webview_kiosk.ui.components.setting.fielditems.jsscript.ApplyAppThemeSetting
import com.nktnet.webview_kiosk.ui.components.setting.fielditems.jsscript.ApplyDesktopViewportWidthSetting
import com.nktnet.webview_kiosk.ui.components.setting.fielditems.jsscript.CustomScriptOnPageFinishSetting
import com.nktnet.webview_kiosk.ui.components.setting.fielditems.jsscript.CustomScriptOnPageStartSetting
import com.nktnet.webview_kiosk.ui.components.setting.fielditems.jsscript.EnableBatteryApiSetting
import com.nktnet.webview_kiosk.ui.components.setting.fielditems.jsscript.EnableBrightnessApiSetting

@Composable
fun SettingsJsScriptsScreen(
    navController: NavController,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(top = 4.dp)
            .padding(horizontal = 16.dp),
    ) {
        SettingLabel(
            navController = navController,
            label = stringResource(R.string.settings_js_scripts_title)
        )
        SettingDivider()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            ApplyAppThemeSetting()
            ApplyDesktopViewportWidthSetting()
            EnableBatteryApiSetting()
            EnableBrightnessApiSetting()
            CustomScriptOnPageStartSetting()
            CustomScriptOnPageFinishSetting()

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
