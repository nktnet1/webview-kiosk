package uk.nktnet.webviewkiosk.ui.view

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
import uk.nktnet.webviewkiosk.ui.components.setting.fielditems.webcontent.AllowLocalFilesSetting
import uk.nktnet.webviewkiosk.ui.components.setting.fielditems.webcontent.BlacklistSetting
import uk.nktnet.webviewkiosk.ui.components.setting.fielditems.webcontent.BookmarksSetting
import uk.nktnet.webviewkiosk.ui.components.setting.fielditems.webcontent.HomeUrlSetting
import uk.nktnet.webviewkiosk.ui.components.setting.fielditems.webcontent.WhitelistSetting

@Composable
fun SettingsWebContentScreen(navController: NavController) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .windowInsetsPadding(WindowInsets.safeContent)
            .verticalScroll(rememberScrollState())
    ) {
        SettingLabel(navController = navController, label = "Web Content")

        SettingDivider()

        HomeUrlSetting()
        BlacklistSetting()
        WhitelistSetting()
        BookmarksSetting()
        AllowLocalFilesSetting()

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = { navController.navigate(Screen.SettingsWebContentFiles.route) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Manage Local Files")
        }
    }
}
