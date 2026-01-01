package uk.nktnet.webviewkiosk.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import uk.nktnet.webviewkiosk.R
import uk.nktnet.webviewkiosk.config.Screen
import uk.nktnet.webviewkiosk.ui.components.setting.SettingDivider
import uk.nktnet.webviewkiosk.ui.components.setting.SettingLabel
import uk.nktnet.webviewkiosk.ui.components.setting.fielditems.webbrowsing.AddressBarActionsSetting
import uk.nktnet.webviewkiosk.ui.components.setting.fielditems.webbrowsing.AllowBackwardsNavigationSetting
import uk.nktnet.webviewkiosk.ui.components.setting.fielditems.webbrowsing.AllowBookmarkAccessSetting
import uk.nktnet.webviewkiosk.ui.components.setting.fielditems.webbrowsing.AllowDefaultLongPressSetting
import uk.nktnet.webviewkiosk.ui.components.setting.fielditems.webbrowsing.AllowGoHomeSetting
import uk.nktnet.webviewkiosk.ui.components.setting.fielditems.webbrowsing.AllowHistoryAccessSetting
import uk.nktnet.webviewkiosk.ui.components.setting.fielditems.webbrowsing.AllowLinkLongPressContextMenuSetting
import uk.nktnet.webviewkiosk.ui.components.setting.fielditems.webbrowsing.AllowOtherUrlSchemesSetting
import uk.nktnet.webviewkiosk.ui.components.setting.fielditems.webbrowsing.AllowPullToRefreshSetting
import uk.nktnet.webviewkiosk.ui.components.setting.fielditems.webbrowsing.AllowRefreshSetting
import uk.nktnet.webviewkiosk.ui.components.setting.fielditems.webbrowsing.ClearHistoryOnHomeSetting
import uk.nktnet.webviewkiosk.ui.components.setting.fielditems.webbrowsing.KioskControlPanelActionsSetting
import uk.nktnet.webviewkiosk.ui.components.setting.fielditems.webbrowsing.KioskControlPanelRegionSetting
import uk.nktnet.webviewkiosk.ui.components.setting.fielditems.webbrowsing.OverrideUrlLoadingBlockActionSetting
import uk.nktnet.webviewkiosk.ui.components.setting.fielditems.webbrowsing.ReplaceHistoryUrlOnRedirectSetting
import uk.nktnet.webviewkiosk.ui.components.setting.fielditems.webbrowsing.SearchProviderUrlSetting
import uk.nktnet.webviewkiosk.ui.components.setting.fielditems.webbrowsing.SearchSuggestionEngineSetting

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
