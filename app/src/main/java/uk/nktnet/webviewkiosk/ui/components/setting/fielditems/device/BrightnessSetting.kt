package uk.nktnet.webviewkiosk.ui.components.setting.fielditems.device

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.UserSettingsKeys
import uk.nktnet.webviewkiosk.ui.components.setting.fields.NumberSettingFieldItem
import uk.nktnet.webviewkiosk.utils.setWindowBrightness
import kotlin.math.roundToInt

@Composable
fun BrightnessSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }

    NumberSettingFieldItem(
        label = "Brightness",
        infoText = """
            Set the app window brightness from 0 (very dim) to 100 (very dark).
            
            Use -1 to disable (i.e. the system default brightness will be used).
        """.trimIndent(),
        initialValue = userSettings.brightness,
        restricted = userSettings.isRestricted(UserSettingsKeys.Device.BRIGHTNESS),
        min = -1,
        max = 100,
        placeholder = "e.g. 20",
        descriptionFormatter = { v ->
            if (v == "-1") {
                "-1 (system default)"
            } else {
                v
            }
        },
        onSave = { value ->
            userSettings.brightness = value
            setWindowBrightness(context, value)
        },
        extraContent = { draftValue, setValue ->
            Column {
                Slider(
                    value = draftValue.toFloatOrNull() ?: -1f,
                    onValueChange = { newValue ->
                        setValue(newValue.roundToInt().toString())
                    },
                    valueRange = -1f..100f,
                    steps = 95,
                    modifier = Modifier.fillMaxWidth()
                )

                Button(
                    enabled = draftValue.isNotEmpty() && draftValue != "-1",
                    onClick = { setValue("-1") },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Use System Default (-1)")
                }
            }
        }
    )
}
