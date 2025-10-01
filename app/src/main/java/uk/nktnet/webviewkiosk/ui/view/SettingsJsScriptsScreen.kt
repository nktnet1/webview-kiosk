package uk.nktnet.webviewkiosk.ui.view

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import uk.nktnet.webviewkiosk.ui.components.setting.SettingDivider
import uk.nktnet.webviewkiosk.ui.components.setting.SettingLabel
import uk.nktnet.webviewkiosk.ui.components.setting.fielditems.jsscript.ApplyAppThemeSetting
import uk.nktnet.webviewkiosk.ui.components.setting.fielditems.jsscript.ApplyDesktopViewportSetting
import uk.nktnet.webviewkiosk.ui.components.setting.fielditems.jsscript.CustomScriptOnFinishSetting
import uk.nktnet.webviewkiosk.ui.components.setting.fielditems.jsscript.CustomScriptOnStartSetting

@Composable
fun SettingsJsScriptsScreen(
    navController: NavController,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .windowInsetsPadding(WindowInsets.safeContent)
            .verticalScroll(rememberScrollState()),
    ) {
        SettingLabel(navController = navController, label = "JS Scripts")

        SettingDivider()

        ApplyAppThemeSetting()
        ApplyDesktopViewportSetting()
        CustomScriptOnStartSetting()
        CustomScriptOnFinishSetting()
    }
}
