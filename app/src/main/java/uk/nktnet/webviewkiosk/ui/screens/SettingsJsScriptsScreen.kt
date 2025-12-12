package uk.nktnet.webviewkiosk.ui.screens

import androidx.compose.foundation.layout.*
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
import uk.nktnet.webviewkiosk.ui.components.setting.fielditems.jsscript.ApplyAppThemeSetting
import uk.nktnet.webviewkiosk.ui.components.setting.fielditems.jsscript.ApplyDesktopViewportWidthSetting
import uk.nktnet.webviewkiosk.ui.components.setting.fielditems.jsscript.CustomScriptOnPageFinishSetting
import uk.nktnet.webviewkiosk.ui.components.setting.fielditems.jsscript.CustomScriptOnPageStartSetting
import uk.nktnet.webviewkiosk.ui.components.setting.fielditems.jsscript.EnableBatteryApiSetting
import uk.nktnet.webviewkiosk.ui.components.setting.fielditems.jsscript.EnableBrightnessApiSetting

@Composable
fun SettingsJsScriptsScreen(
    navController: NavController,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeContent)
            .padding(horizontal = 16.dp)
    ) {
        SettingLabel(
            navController = navController,
            label = stringResource(R.string.settings_js_scripts_title)
        )
        SettingDivider()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            ApplyAppThemeSetting()
            ApplyDesktopViewportWidthSetting()
            EnableBatteryApiSetting()
            EnableBrightnessApiSetting()
            CustomScriptOnPageStartSetting()
            CustomScriptOnPageFinishSetting()

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
