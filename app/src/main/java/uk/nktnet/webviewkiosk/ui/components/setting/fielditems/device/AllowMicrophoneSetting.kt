package uk.nktnet.webviewkiosk.ui.components.setting.fielditems.device

import android.Manifest
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import uk.nktnet.webviewkiosk.R
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.UserSettingsKeys
import uk.nktnet.webviewkiosk.ui.components.setting.fields.BooleanSettingFieldItem
import uk.nktnet.webviewkiosk.utils.rememberPermissionState

@Composable
fun AllowMicrophoneSetting() {
    val context = LocalContext.current
    val userSettings = UserSettings(context)
    val settingKey = UserSettingsKeys.Device.ALLOW_MICROPHONE

    val (permissionState, requestPermission) =
        rememberPermissionState(Manifest.permission.RECORD_AUDIO)

    BooleanSettingFieldItem(
        initialValue = userSettings.allowMicrophone,
        settingKey = settingKey,
        restricted = userSettings.isRestricted(settingKey),
        onSave = { userSettings.allowMicrophone = it },
        itemText = { v ->
            val statusText =
                if (permissionState.granted) {
                    ""
                } else {
                    context.getString(R.string.ui_no_permission)
                }
            if (v) {
                context.getString(R.string.ui_true_value, statusText)
            } else {
                context.getString(R.string.ui_false_value, statusText)
            }
        },
        extraContent = {
            Button(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                onClick = requestPermission,
            ) {
                val buttonText =
                    when {
                        permissionState.granted -> {
                            stringResource(R.string.ui_disable_in_app_info)
                        }
                        !permissionState.granted &&
                            !permissionState.shouldShowRationale -> {
                            stringResource(R.string.ui_request_microphone_permission)
                        }
                        else -> {
                            stringResource(R.string.ui_enable_in_app_info)
                        }
                    }
                Text(
                    text = buttonText,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        },
    )
}
