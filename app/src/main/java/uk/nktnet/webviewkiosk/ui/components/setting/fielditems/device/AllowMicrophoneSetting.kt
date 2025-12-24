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
fun AllowMicrophoneSetting() {
    val context = LocalContext.current
    val userSettings = UserSettings(context)
    val settingKey = UserSettingsKeys.Device.ALLOW_MICROPHONE

    val (
        permissionState,
        requestPermission
    ) = rememberPermissionState(Manifest.permission.RECORD_AUDIO)

    BooleanSettingFieldItem(
        label = stringResource(id = R.string.device_allow_microphone_title),
        infoText = """
            Set to true to give WebView access to your device's microphone.

            You will need to grant the RECORD_AUDIO permission, which is required for the
            WebView's RESOURCE_AUDIO_CAPTURE feature.
        """.trimIndent(),
        initialValue = userSettings.allowMicrophone,
        settingKey = settingKey,
        restricted = userSettings.isRestricted(settingKey),
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
                    permissionState.granted -> {
                        "Disable in App Info"
                    }
                    !permissionState.granted && !permissionState.shouldShowRationale -> {
                        "Request Microphone Permission"
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
