package uk.nktnet.webviewkiosk.ui.components.setting.fielditems.mqtt.connection

import android.content.ClipData
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.toClipEntry
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import uk.nktnet.webviewkiosk.config.Constants
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.UserSettingsKeys
import uk.nktnet.webviewkiosk.config.option.MqttVariableNameOption
import uk.nktnet.webviewkiosk.mqtt.MqttManager
import uk.nktnet.webviewkiosk.ui.components.setting.fields.TextSettingFieldItem

@Composable
fun MqttClientIdSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }

    val restricted = userSettings.isRestricted(UserSettingsKeys.Mqtt.Connection.CLIENT_ID)

    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()

    val recommendedClientId = "wk-${'$'}{${MqttVariableNameOption.APP_INSTANCE_ID.name}}"

    TextSettingFieldItem(
        label = "Client ID",
        infoText = """
            A unique identifier for this client when connecting to the MQTT broker.

            Leave this field blank if you want the broker server to generate a
            client ID for ${Constants.APP_NAME}.

            Supports global variables such as APP_INSTANCE_ID and USERNAME, which
            you can use like:
            - $recommendedClientId
        """.trimIndent(),
        placeholder = "e.g. wk-${'$'}{APP_INSTANCE_ID}",
        initialValue = userSettings.mqttClientId,
        descriptionFormatter = {
            if (it.trim().isEmpty()) {
                "(blank)"
            } else {
                MqttManager.mqttVariableReplacement(it)
            }
        },
        restricted = restricted,
        isMultiline = false,
        onLongClick = { v ->
            scope.launch {
                val clipData = ClipData.newPlainText(
                    "MQTT Client ID",
                    MqttManager.mqttVariableReplacement(v)
                )
                clipboard.setClipEntry(clipData.toClipEntry())
            }
        },
        onSave = { userSettings.mqttClientId = it },
        extraContent = { setValue: (String) -> Unit ->
            if (restricted) return@TextSettingFieldItem
            Button(
                onClick = { setValue(recommendedClientId) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                shape = RoundedCornerShape(4.dp),
                colors = ButtonDefaults.buttonColors()
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Use recommended:",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = recommendedClientId,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    )
}
