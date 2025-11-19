package uk.nktnet.webviewkiosk.ui.components.setting.fielditems.device.owner.locktaskfeature

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.UserSettingsKeys
import uk.nktnet.webviewkiosk.ui.components.setting.fields.BooleanSettingFieldItem

@Composable
fun LockTaskFeatureSystemInfoSetting() {
    val context = LocalContext.current
    val userSettings = UserSettings(context)

    BooleanSettingFieldItem(
        label = "Show System Info",
        infoText = """
            Enables the status bar’s system info area that contains indicators
            such as connectivity, battery, and sound/vibrate options.
        """.trimIndent(),
        initialValue = userSettings.lockTaskFeatureSystemInfo,
        restricted = userSettings.isRestricted(UserSettingsKeys.Device.Owner.LockTaskFeature.SYSTEM_INFO),
        onSave = { userSettings.lockTaskFeatureSystemInfo = it },
        itemText = { v -> if (v) "Enabled" else "Disabled" },
        extraContent = {
            Button(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                onClick = { userSettings.lockTaskFeatureSystemInfo = !userSettings.lockTaskFeatureSystemInfo }
            ) {
                Text(
                    text = if (userSettings.lockTaskFeatureSystemInfo) "Disable" else "Enable",
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
    )
}
