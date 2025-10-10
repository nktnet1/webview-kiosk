package uk.nktnet.webviewkiosk.ui.components.setting.fielditems.device

import android.Manifest
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.ui.components.setting.fields.BooleanSettingFieldItem
import uk.nktnet.webviewkiosk.utils.rememberPermissionState

@Composable
fun AllowCameraSetting() {
    val context = LocalContext.current
    val userSettings = UserSettings(context)

    val (
        permissionState,
        requestPermission
    ) = rememberPermissionState(Manifest.permission.CAMERA)

    BooleanSettingFieldItem(
        label = "Allow Camera",
        infoText = """
            Set to true to allow the WebView to access the device camera.
            This is required if your web content needs camera permissions.
        """.trimIndent(),
        initialValue = userSettings.allowCamera,
        onSave = { userSettings.allowCamera = it },
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
                    !permissionState.granted && !permissionState.shouldShowRationale -> "Request Camera Permission"
                    else -> "Enable in App Settings"
                }
                Text(buttonText)
            }
        }
    )
}
