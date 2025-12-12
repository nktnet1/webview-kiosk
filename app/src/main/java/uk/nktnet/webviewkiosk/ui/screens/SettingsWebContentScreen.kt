package uk.nktnet.webviewkiosk.ui.screens

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
import uk.nktnet.webviewkiosk.R
import uk.nktnet.webviewkiosk.config.Screen
import uk.nktnet.webviewkiosk.ui.components.setting.SettingDivider
import uk.nktnet.webviewkiosk.ui.components.setting.SettingLabel
import uk.nktnet.webviewkiosk.ui.components.setting.fielditems.webcontent.AllowLocalFilesSetting
import uk.nktnet.webviewkiosk.ui.components.setting.fielditems.webcontent.WebsiteBlacklistSetting
import uk.nktnet.webviewkiosk.ui.components.setting.fielditems.webcontent.WebsiteBookmarksSetting
import uk.nktnet.webviewkiosk.ui.components.setting.fielditems.webcontent.HomeUrlSetting
import uk.nktnet.webviewkiosk.ui.components.setting.fielditems.webcontent.WebsiteWhitelistSetting

@Composable
fun SettingsWebContentScreen(navController: NavController) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeContent)
            .padding(horizontal = 16.dp)
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
            AllowLocalFilesSetting()

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = { navController.navigate(Screen.SettingsWebContentFiles.route) },
                modifier = Modifier.fillMaxWidth().height(50.dp),
            ) {
                Text("Manage Local Files")
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
