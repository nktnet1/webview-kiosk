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
import uk.nktnet.webviewkiosk.ui.components.setting.SettingDivider
import uk.nktnet.webviewkiosk.ui.components.setting.SettingLabel
import uk.nktnet.webviewkiosk.ui.components.setting.fielditems.webengine.AcceptCookiesSetting
import uk.nktnet.webviewkiosk.ui.components.setting.fielditems.webengine.AcceptThirdPartyCookiesSetting
import uk.nktnet.webviewkiosk.ui.components.setting.fielditems.webengine.AllowFileAccessFromFileURLsSetting
import uk.nktnet.webviewkiosk.ui.components.setting.fielditems.webengine.AllowFilePickerSetting
import uk.nktnet.webviewkiosk.ui.components.setting.fielditems.webengine.AllowUniversalAccessFromFileURLsSetting
import uk.nktnet.webviewkiosk.ui.components.setting.fielditems.webengine.BuiltInZoomControlsSetting
import uk.nktnet.webviewkiosk.ui.components.setting.fielditems.webengine.CacheModeSetting
import uk.nktnet.webviewkiosk.ui.components.setting.fielditems.webengine.DisplayZoomControlsSetting
import uk.nktnet.webviewkiosk.ui.components.setting.fielditems.webengine.EnableDomStorageSetting
import uk.nktnet.webviewkiosk.ui.components.setting.fielditems.webengine.EnableJavaScriptSetting
import uk.nktnet.webviewkiosk.ui.components.setting.fielditems.webengine.InitialScaleSetting
import uk.nktnet.webviewkiosk.ui.components.setting.fielditems.webengine.LayoutAlgorithmSetting
import uk.nktnet.webviewkiosk.ui.components.setting.fielditems.webengine.LoadWithOverviewModeSetting
import uk.nktnet.webviewkiosk.ui.components.setting.fielditems.webengine.MediaPlaybackRequiresUserGestureSetting
import uk.nktnet.webviewkiosk.ui.components.setting.fielditems.webengine.MixedContentModeSetting
import uk.nktnet.webviewkiosk.ui.components.setting.fielditems.webengine.OverScrollModeSetting
import uk.nktnet.webviewkiosk.ui.components.setting.fielditems.webengine.RequestFocusOnPageStartSetting
import uk.nktnet.webviewkiosk.ui.components.setting.fielditems.webengine.SslErrorModeSetting
import uk.nktnet.webviewkiosk.ui.components.setting.fielditems.webengine.SupportZoomSetting
import uk.nktnet.webviewkiosk.ui.components.setting.fielditems.webengine.UseWideViewPortSetting
import uk.nktnet.webviewkiosk.ui.components.setting.fielditems.webengine.UserAgentSetting

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
            AllowFilePickerSetting()
            MediaPlaybackRequiresUserGestureSetting()
            SslErrorModeSetting()
            MixedContentModeSetting()
            OverScrollModeSetting()
            RequestFocusOnPageStartSetting()

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
