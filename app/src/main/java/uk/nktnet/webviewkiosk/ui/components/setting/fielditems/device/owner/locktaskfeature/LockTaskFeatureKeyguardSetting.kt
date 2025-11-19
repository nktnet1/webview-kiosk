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
fun LockTaskFeatureKeyguardSetting() {
    val context = LocalContext.current
    val userSettings = UserSettings(context)

    BooleanSettingFieldItem(
        label = "Enable Keyguard",
        infoText = """
            Enables any lock screen that might be set on the device. 
            Typically not suitable for devices with public users such as kiosks or digital signage.
        """.trimIndent(),
        initialValue = userSettings.lockTaskFeatureKeyguard,
        restricted = userSettings.isRestricted(UserSettingsKeys.Device.Owner.LockTaskFeature.KEYGUARD),
        onSave = { userSettings.lockTaskFeatureKeyguard = it },
        itemText = { v -> if (v) "Enabled" else "Disabled" },
        extraContent = {
            Button(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                onClick = { userSettings.lockTaskFeatureKeyguard = !userSettings.lockTaskFeatureKeyguard }
            ) {
                Text(
                    text = if (userSettings.lockTaskFeatureKeyguard) "Disable" else "Enable",
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
    )
}
