package uk.nktnet.webviewkiosk.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import uk.nktnet.webviewkiosk.config.Screen
import uk.nktnet.webviewkiosk.ui.components.setting.SettingDivider
import uk.nktnet.webviewkiosk.ui.components.setting.SettingLabel
import uk.nktnet.webviewkiosk.ui.components.setting.fielditems.webbrowsing.*

@Composable
fun SettingsWebBrowsingScreen(
    navController: NavController,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeContent)
            .padding(horizontal = 16.dp)
    ) {
        SettingLabel(navController = navController, label = "Web Browsing")
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
                modifier = Modifier.fillMaxWidth().height(50.dp),
            ) {
                Text("Manage Site Permissions")
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
