package uk.nktnet.webviewkiosk.ui.components.setting.fielditems.device

import android.Manifest
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.ui.components.setting.fields.BooleanSettingFieldItem
import uk.nktnet.webviewkiosk.utils.rememberPermissionState

@Composable
fun AllowMicrophoneSetting() {
    val context = LocalContext.current
    val userSettings = UserSettings(context)

    val (
        permissionState,
        requestPermission
    ) = rememberPermissionState(Manifest.permission.RECORD_AUDIO)

    BooleanSettingFieldItem(
        label = "Allow Microphone",
        infoText = """
            Set to true to allow the WebView to access the device microphone.
            
            This will enable the RESOURCE_AUDIO_CAPTURE permission.
        """.trimIndent(),
        initialValue = userSettings.allowMicrophone,
        onSave = { userSettings.allowMicrophone = it },
        itemText = { v ->
            val statusText = if (permissionState.granted) "" else "(no permission)"
            if (v) "True $statusText" else "False $statusText"
        },
        extraContent = {
            Button(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                onClick = requestPermission
            ) {
                val buttonText = when {
                    permissionState.granted -> "Disable in App Settings"
                    !permissionState.granted && !permissionState.shouldShowRationale -> "Request Microphone Permission"
                    else -> "Enable in App Settings"
                }
                Text(
                    text = buttonText,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
    )
}
