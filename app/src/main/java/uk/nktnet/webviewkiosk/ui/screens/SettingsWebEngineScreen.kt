package uk.nktnet.webviewkiosk.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import uk.nktnet.webviewkiosk.ui.components.setting.SettingDivider
import uk.nktnet.webviewkiosk.ui.components.setting.SettingLabel
import uk.nktnet.webviewkiosk.ui.components.setting.fielditems.webengine.*

@Composable
fun SettingsWebEngineScreen(navController: NavController) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .windowInsetsPadding(WindowInsets.safeContent)
            .verticalScroll(rememberScrollState())
    ) {
        SettingLabel(navController = navController, label = "Web Engine")

        SettingDivider()

        EnableJavaScriptSetting()
        EnableDomStorageSetting()
        AcceptCookiesSetting()
        AcceptThirdPartyCookiesSetting()
        CacheModeSetting()
        UserAgentSetting()
        LayoutAlgorithmSetting()
        UseWideViewPortSetting()
        LoadWithOverviewModeSetting()
        EnableZoomSetting()
        DisplayZoomControlsSetting()
        AllowFileAccessFromFileURLsSetting()
        AllowUniversalAccessFromFileURLsSetting()
        MediaPlaybackRequiresUserGestureSetting()
        SslErrorModeSetting()
        EnableBatteryApiSetting()
    }
}
