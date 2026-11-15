package uk.nktnet.webviewkiosk.ui.components.setting.unifiedpush

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import uk.nktnet.webviewkiosk.R
import uk.nktnet.webviewkiosk.config.Screen
import uk.nktnet.webviewkiosk.ui.components.setting.SettingListItem

@Composable
fun UnifiedPushDebugLogCard(navController: NavController) {
    SettingListItem(
        title = stringResource(R.string.settings_debug_log_title),
        description =
            stringResource(R.string.settings_unifiedpush_debug_log_description),
        modifier = Modifier.padding(bottom = 10.dp),
        onClick = {
            navController.navigate(Screen.SettingsUnifiedPushDebug.route)
        },
    )
}
