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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import uk.nktnet.webviewkiosk.R
import uk.nktnet.webviewkiosk.ui.components.setting.mqtt.MqttControlButtons
import uk.nktnet.webviewkiosk.ui.components.setting.SettingDivider
import uk.nktnet.webviewkiosk.ui.components.setting.SettingLabel
import uk.nktnet.webviewkiosk.ui.components.setting.fielditems.mqtt.will.MqttWillDelayIntervalSetting
import uk.nktnet.webviewkiosk.ui.components.setting.fielditems.mqtt.will.MqttWillMessageExpiryIntervalSetting
import uk.nktnet.webviewkiosk.ui.components.setting.fielditems.mqtt.will.MqttWillPayloadSetting
import uk.nktnet.webviewkiosk.ui.components.setting.fielditems.mqtt.will.MqttWillQosSetting
import uk.nktnet.webviewkiosk.ui.components.setting.fielditems.mqtt.will.MqttWillRetainSetting
import uk.nktnet.webviewkiosk.ui.components.setting.fielditems.mqtt.will.MqttWillTopicSetting
import uk.nktnet.webviewkiosk.ui.components.setting.mqtt.MqttDebugLogsButton

@Composable
fun SettingsMqttWillScreen(navController: NavController) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(top = 4.dp)
            .padding(horizontal = 16.dp),
    ) {
        SettingLabel(
            navController = navController,
            label = stringResource(R.string.settings_mqtt_will_title)
        )
        SettingDivider()

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            MqttControlButtons()
            HorizontalDivider(modifier = Modifier.padding(top = 8.dp))

            MqttWillTopicSetting()
            MqttWillQosSetting()
            MqttWillPayloadSetting()
            MqttWillRetainSetting()
            MqttWillMessageExpiryIntervalSetting()
            MqttWillDelayIntervalSetting()

            Spacer(modifier = Modifier.height(8.dp))
        }

        MqttDebugLogsButton(navController)
    }
}
