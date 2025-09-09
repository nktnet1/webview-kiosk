package uk.nktnet.webviewkiosk.ui.view

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import uk.nktnet.webviewkiosk.ui.components.setting.SettingLabel
import uk.nktnet.webviewkiosk.ui.components.setting.fielditems.appearance.AddressBarModeSetting
import uk.nktnet.webviewkiosk.ui.components.setting.fielditems.appearance.BlockedMessageSetting
import uk.nktnet.webviewkiosk.ui.components.setting.fielditems.appearance.ThemeSetting
import uk.nktnet.webviewkiosk.ui.components.setting.fielditems.appearance.WebViewInsetSetting
import androidx.compose.runtime.MutableState
import uk.nktnet.webviewkiosk.config.option.ThemeOption
import uk.nktnet.webviewkiosk.ui.components.setting.SettingDivider

@Composable
fun SettingsAppearanceScreen(
    navController: NavController,
    themeState: MutableState<ThemeOption>,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .windowInsetsPadding(WindowInsets.safeContent)
            .verticalScroll(rememberScrollState())
    ) {
        SettingLabel(navController = navController, label = "Appearance")

        SettingDivider()

        ThemeSetting(themeState)
        AddressBarModeSetting()
        WebViewInsetSetting()
        BlockedMessageSetting()
    }
}
