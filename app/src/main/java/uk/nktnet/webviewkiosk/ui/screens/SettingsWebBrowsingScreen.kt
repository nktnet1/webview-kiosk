package com.nktnet.webview_kiosk.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.nktnet.webview_kiosk.R
import com.nktnet.webview_kiosk.config.Screen
import com.nktnet.webview_kiosk.ui.components.setting.SettingDivider
import com.nktnet.webview_kiosk.ui.components.setting.SettingLabel
import com.nktnet.webview_kiosk.ui.components.setting.fielditems.webbrowsing.*

@Composable
fun SettingsWebBrowsingScreen(
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
            label = stringResource(R.string.settings_web_browsing_title)
        )
        SettingDivider()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            AllowRefreshSetting()
            AllowPullToRefreshSetting()
            AllowBackwardsNavigationSetting()
            AllowGoHomeSetting()
            ClearHistoryOnHomeSetting()
            ReplaceHistoryUrlOnRedirectSetting()
            AllowHistoryAccessSetting()
            AllowBookmarkAccessSetting()
            AllowOtherUrlSchemesSetting()
            AllowDefaultLongPressSetting()
            AllowLinkLongPressContextMenuSetting()
            OverrideUrlLoadingBlockActionSetting()
            AddressBarActionsSetting()
            KioskControlPanelRegionSetting()
            KioskControlPanelActionsSetting()
            SearchProviderUrlSetting()
            SearchSuggestionEngineSetting()

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = { navController.navigate(Screen.SettingsWebBrowsingSitePermissions.route) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Manage Site Permissions")
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
