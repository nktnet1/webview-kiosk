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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import uk.nktnet.webviewkiosk.R
import uk.nktnet.webviewkiosk.ui.components.setting.SettingDivider
import uk.nktnet.webviewkiosk.ui.components.setting.SettingLabel
import uk.nktnet.webviewkiosk.ui.components.setting.UnifiedPushControlButtons
import uk.nktnet.webviewkiosk.ui.components.setting.fielditems.unifiedpush.UnifiedPushDistributorSetting
import uk.nktnet.webviewkiosk.ui.components.setting.fielditems.unifiedpush.UnifiedPushEnabledSetting
import uk.nktnet.webviewkiosk.ui.components.setting.fielditems.unifiedpush.UnifiedPushInstanceSetting
import uk.nktnet.webviewkiosk.ui.components.setting.fielditems.unifiedpush.UnifiedPushMessageForDistributorSetting
import uk.nktnet.webviewkiosk.ui.components.setting.fielditems.unifiedpush.UnifiedPushProcessUnencryptedMessagesSetting
import uk.nktnet.webviewkiosk.ui.components.setting.fielditems.unifiedpush.UnifiedPushStoreEndpointCredentialsSetting
import uk.nktnet.webviewkiosk.ui.components.setting.fielditems.unifiedpush.UnifiedPushVapidPublicKeySetting
import uk.nktnet.webviewkiosk.ui.components.setting.unifiedpush.UnifiedPushDebugLogsButton

@Composable
fun SettingsUnifiedPushScreen(navController: NavController) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(top = 4.dp)
            .padding(horizontal = 16.dp),
    ) {
        SettingLabel(
            navController = navController,
            label = stringResource(R.string.settings_unifiedpush_title)
        )
        SettingDivider()

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            UnifiedPushControlButtons()

            UnifiedPushEnabledSetting()
            UnifiedPushDistributorSetting()
            UnifiedPushInstanceSetting()
            UnifiedPushMessageForDistributorSetting()
            UnifiedPushVapidPublicKeySetting()
            UnifiedPushProcessUnencryptedMessagesSetting()
            UnifiedPushStoreEndpointCredentialsSetting()

            Spacer(modifier = Modifier.height(8.dp))
        }

        UnifiedPushDebugLogsButton(navController)
    }
}
