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
fun LockTaskFeatureHomeSetting() {
    val context = LocalContext.current
    val userSettings = UserSettings(context)

    BooleanSettingFieldItem(
        label = "Show Home Button",
        infoText = """
            Shows the Home button.
            
            Enable for custom launchers - tapping an enabled Home button has no
            action unless you allowlist the default Android launcher.
        """.trimIndent(),
        initialValue = userSettings.lockTaskFeatureHome,
        restricted = userSettings.isRestricted(UserSettingsKeys.Device.Owner.LockTaskFeature.HOME),
        onSave = { userSettings.lockTaskFeatureHome = it },
        itemText = { v -> if (v) "Enabled" else "Disabled" },
        extraContent = {
            Button(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                onClick = { userSettings.lockTaskFeatureHome = !userSettings.lockTaskFeatureHome }
            ) {
                Text(
                    text = if (userSettings.lockTaskFeatureHome) "Disable" else "Enable",
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
    )
}
