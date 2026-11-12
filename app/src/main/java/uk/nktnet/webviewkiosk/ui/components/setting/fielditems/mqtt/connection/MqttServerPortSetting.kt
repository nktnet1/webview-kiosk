package uk.nktnet.webviewkiosk.ui.components.setting.fielditems.mqtt.connection

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import uk.nktnet.webviewkiosk.R
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.UserSettingsKeys
import uk.nktnet.webviewkiosk.ui.components.setting.fields.NumberSettingFieldItem
import uk.nktnet.webviewkiosk.utils.normaliseInfoText

@Composable
fun MqttServerPortSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }
    val settingKey = UserSettingsKeys.Mqtt.Connection.SERVER_PORT

    NumberSettingFieldItem(
        initialValue = userSettings.mqttServerPort,
        settingKey = settingKey,
        restricted = userSettings.isRestricted(settingKey),
        min = 1,
        max = 65535,
        placeholder = stringResource(R.string.ui_e_g_1883),
        onSave = { userSettings.mqttServerPort = it },
        extraContent = { v, setValue ->
            val tcpPorts =
                listOf(
                    1883,
                    8883,
                )
            val webSocketPorts =
                listOf(
                    80,
                    443,
                    8083,
                    8084,
                    1887,
                    8000,
                    8080,
                    8081,
                    8090,
                    8091,
                    8443,
                    9001,
                    15675,
                    15676,
                )
            val commonPorts = tcpPorts + webSocketPorts

            val visibleNum = 6
            val visiblePorts = commonPorts.take(visibleNum)
            val additionalPorts = commonPorts.drop(visibleNum)

            var moreExpanded by remember { mutableStateOf(false) }

            FlowRow(
                modifier =
                    Modifier.fillMaxWidth()
                        .padding(top = 8.dp)
                        .wrapContentHeight(align = Alignment.Top),
                horizontalArrangement =
                    Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy((-8).dp),
                maxItemsInEachRow = 4,
            ) {
                visiblePorts.forEach { port ->
                    FilterChip(
                        selected = v.toIntOrNull() == port,
                        onClick = {
                            if (v.toIntOrNull() != port) {
                                setValue(port.toString())
                            } else {
                                setValue("")
                            }
                        },
                        label = {
                            Text(port.toString())
                        },
                    )
                }

                Box {
                    FilterChip(
                        selected = v.toIntOrNull() in additionalPorts,
                        onClick = {
                            moreExpanded = true
                        },
                        label = {
                            Text(stringResource(R.string.ui_more))
                        },
                    )

                    DropdownMenu(
                        expanded = moreExpanded,
                        onDismissRequest = {
                            moreExpanded = false
                        },
                    ) {
                        additionalPorts.forEach { port ->
                            DropdownMenuItem(
                                text = {
                                    Text(port.toString())
                                },
                                onClick = {
                                    if (v.toIntOrNull() != port) {
                                        setValue(port.toString())
                                        moreExpanded = false
                                    } else {
                                        setValue("")
                                    }
                                },
                                colors =
                                    if (v.toIntOrNull() == port) {
                                        MenuDefaults.itemColors(
                                            textColor =
                                                MaterialTheme.colorScheme.primary
                                        )
                                    } else {
                                        MenuDefaults.itemColors()
                                    },
                            )
                        }
                    }
                }
            }

            if (v.toIntOrNull() in webSocketPorts) {
                Row(
                    modifier = Modifier.padding(top = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.baseline_info_24),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary,
                    )
                    Text(
                        text =
                            normaliseInfoText(
                                stringResource(
                                    R.string.ui_mqtt_websocket_port_warning,
                                    v,
                                )
                            ),
                        color = MaterialTheme.colorScheme.tertiary,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
    )
}
