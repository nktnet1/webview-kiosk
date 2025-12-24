package com.nktnet.webview_kiosk.ui.components.setting.fielditems.device

import android.Manifest
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.nktnet.webview_kiosk.R
import com.nktnet.webview_kiosk.config.UserSettings
import com.nktnet.webview_kiosk.config.UserSettingsKeys
import com.nktnet.webview_kiosk.ui.components.setting.fields.BooleanSettingFieldItem
import com.nktnet.webview_kiosk.utils.rememberPermissionState

@Composable
fun AllowCameraSetting() {
    val context = LocalContext.current
    val userSettings = UserSettings(context)
    val settingKey = UserSettingsKeys.Device.ALLOW_CAMERA

    val (
        permissionState,
        requestPermission
    ) = rememberPermissionState(Manifest.permission.CAMERA)

    BooleanSettingFieldItem(
        label = stringResource(id = R.string.device_allow_camera_title),
        infoText = """
            Set to true to give WebView access to your device's camera.

            You will need to grant the CAMERA permission, which is required for the
            WebView's RESOURCE_VIDEO_CAPTURE feature.
        """.trimIndent(),
        initialValue = userSettings.allowCamera,
        settingKey = settingKey,
        restricted = userSettings.isRestricted(settingKey),
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
                    permissionState.granted -> {
                        "Disable in App Info"
                    }
                    !permissionState.granted && !permissionState.shouldShowRationale -> {
                        "Request Camera Permission"
                    }
                    else -> {
                        "Enable in App Info"
                    }
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
