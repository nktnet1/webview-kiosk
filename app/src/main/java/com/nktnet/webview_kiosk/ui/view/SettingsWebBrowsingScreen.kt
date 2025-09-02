package com.nktnet.webview_kiosk.ui.view

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.nktnet.webview_kiosk.ui.components.setting.SettingDivider
import com.nktnet.webview_kiosk.ui.components.setting.SettingLabel
import com.nktnet.webview_kiosk.ui.components.setting.fields.webbrowsing.*

@Composable
fun SettingsWebBrowsingScreen(
    navController: NavController,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .windowInsetsPadding(WindowInsets.safeContent)
            .verticalScroll(rememberScrollState()),
    ) {
        SettingLabel(navController = navController, label = "Web Browsing")

        SettingDivider()

        AllowRefreshSetting()
        Spacer(modifier = Modifier.height(16.dp))

        AllowBackwardsNavigationSetting()
        Spacer(modifier = Modifier.height(16.dp))

        AllowGoHomeSetting()
        Spacer(modifier = Modifier.height(16.dp))

        ClearHistoryOnHomeSetting()
        Spacer(modifier = Modifier.height(16.dp))

        AllowOtherUrlSchemesSetting()
        Spacer(modifier = Modifier.height(16.dp))

        SearchProviderUrlSetting()
        Spacer(modifier = Modifier.height(12.dp))
    }
}
