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
fun LockTaskFeatureOverviewSetting() {
    val context = LocalContext.current
    val userSettings = UserSettings(context)

    BooleanSettingFieldItem(
        label = "Show Overview Button",
        infoText = """
            Shows the Overview button (tapping this button opens the Recents screen).
            
            If you enable this button, you must also enable the Home button.
        """.trimIndent(),
        initialValue = userSettings.lockTaskFeatureOverview,
        restricted = userSettings.isRestricted(UserSettingsKeys.Device.Owner.LockTaskFeature.OVERVIEW),
        onSave = { userSettings.lockTaskFeatureOverview = it },
        itemText = { v -> if (v) "Enabled" else "Disabled" },
        extraContent = {
            Button(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                onClick = { userSettings.lockTaskFeatureOverview = !userSettings.lockTaskFeatureOverview }
            ) {
                Text(
                    text = if (userSettings.lockTaskFeatureOverview) "Disable" else "Enable",
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
    )
}
