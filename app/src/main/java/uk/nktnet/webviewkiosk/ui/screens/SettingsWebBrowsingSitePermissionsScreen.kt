package uk.nktnet.webviewkiosk.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavController
import uk.nktnet.webviewkiosk.config.SystemSettings
import uk.nktnet.webviewkiosk.ui.components.setting.SettingLabel
import uk.nktnet.webviewkiosk.ui.components.setting.permissions.SitePermissionsList

@Composable
fun SettingsWebBrowsingSitePermissionsScreen(navController: NavController) {
    val context = LocalContext.current
    val systemSettings = remember { SystemSettings(context) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .windowInsetsPadding(WindowInsets.safeContent)
    ) {
        SettingLabel(navController = navController, label = "Site Permissions")

        HorizontalDivider(
            modifier = Modifier.padding(vertical = 8.dp),
            thickness = DividerDefaults.Thickness,
            color = DividerDefaults.color
        )

        SitePermissionsList(
            systemSettings = systemSettings,
            modifier = Modifier.fillMaxSize()
        )
    }
}
