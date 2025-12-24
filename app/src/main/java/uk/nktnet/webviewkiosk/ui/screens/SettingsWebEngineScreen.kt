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
import com.nktnet.webview_kiosk.ui.components.setting.fielditems.webengine.*

@Composable
fun SettingsWebEngineScreen(navController: NavController) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(top = 4.dp)
            .padding(horizontal = 16.dp),
    ) {
        SettingLabel(
            navController = navController,
            label = stringResource(R.string.settings_web_engine_title)
        )
        SettingDivider()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            EnableJavaScriptSetting()
            EnableDomStorageSetting()
            AcceptCookiesSetting()
            AcceptThirdPartyCookiesSetting()
            CacheModeSetting()
            UserAgentSetting()
            LayoutAlgorithmSetting()
            UseWideViewPortSetting()
            LoadWithOverviewModeSetting()
            SupportZoomSetting()
            BuiltInZoomControlsSetting()
            DisplayZoomControlsSetting()
            InitialScaleSetting()
            AllowFileAccessFromFileURLsSetting()
            AllowUniversalAccessFromFileURLsSetting()
            MediaPlaybackRequiresUserGestureSetting()
            SslErrorModeSetting()
            MixedContentModeSetting()
            OverScrollModeSetting()
            RequestFocusOnPageStartSetting()

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
