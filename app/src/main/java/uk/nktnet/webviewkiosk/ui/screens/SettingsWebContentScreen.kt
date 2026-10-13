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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import uk.nktnet.webviewkiosk.R
import uk.nktnet.webviewkiosk.config.Screen
import uk.nktnet.webviewkiosk.ui.components.setting.SettingDivider
import uk.nktnet.webviewkiosk.ui.components.setting.SettingLabel
import uk.nktnet.webviewkiosk.ui.components.setting.SettingListItem
import uk.nktnet.webviewkiosk.ui.components.setting.fielditems.webcontent.AllowLocalFilesSetting
import uk.nktnet.webviewkiosk.ui.components.setting.fielditems.webcontent.HomeUrlSetting
import uk.nktnet.webviewkiosk.ui.components.setting.fielditems.webcontent.SupportPdfRenderingSetting
import uk.nktnet.webviewkiosk.ui.components.setting.fielditems.webcontent.WebsiteBlacklistSetting
import uk.nktnet.webviewkiosk.ui.components.setting.fielditems.webcontent.WebsiteBookmarksSetting
import uk.nktnet.webviewkiosk.ui.components.setting.fielditems.webcontent.WebsiteWhitelistSetting

@Composable
fun SettingsWebContentScreen(navController: NavController) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(top = 4.dp)
            .padding(horizontal = 16.dp),
    ) {
        SettingLabel(
            navController = navController,
            label = stringResource(R.string.settings_web_content_title)
        )
        SettingDivider()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            HomeUrlSetting()
            WebsiteBlacklistSetting()
            WebsiteWhitelistSetting()
            WebsiteBookmarksSetting()
            SupportPdfRenderingSetting()
            AllowLocalFilesSetting()

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(modifier = Modifier.padding(bottom = 12.dp))

            // Use the same sub-settings cards as MQTT's Connection / Topics screens.
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SettingListItem(
                    title = stringResource(R.string.settings_content_blocking_title),
                    description = stringResource(R.string.settings_content_blocking_description),
                    onClick = { navController.navigate(Screen.SettingsContentBlocking.route) },
                )
                SettingListItem(
                    title = stringResource(R.string.settings_files_title),
                    description = stringResource(R.string.settings_web_content_files_description),
                    onClick = { navController.navigate(Screen.SettingsWebContentFiles.route) },
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
