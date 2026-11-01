package uk.nktnet.webviewkiosk.ui.components.setting.fielditems.device

import android.Manifest
import android.os.Build
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
import uk.nktnet.webviewkiosk.utils.PermissionState
import uk.nktnet.webviewkiosk.utils.openAppNotificationsSettings
import uk.nktnet.webviewkiosk.utils.rememberPermissionState

@Composable
fun AllowNotificationsSetting() {
    val context = LocalContext.current
    val userSettings = UserSettings(context)
    val settingKey = UserSettingsKeys.Device.ALLOW_NOTIFICATIONS

    val (permissionState, requestPermission) =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            rememberPermissionState(
                Manifest.permission.POST_NOTIFICATIONS,
                customOpenAction = ::openAppNotificationsSettings,
            )
        } else {
            PermissionState(
                granted = false,
                shouldShowRationale = false,
            ) to
                {
                    openAppNotificationsSettings(context)
                }
        }

    BooleanSettingFieldItem(
        label = stringResource(R.string.device_allow_notifications_title),
        infoText =
            stringResource(
                R.string.setting_info_allow_notifications,
                stringResource(R.string.app_name),
            ),
        initialValue = userSettings.allowNotifications,
        settingKey = settingKey,
        restricted = userSettings.isRestricted(settingKey),
        onSave = { userSettings.allowNotifications = it },
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
                            stringResource(
                                R.string.ui_request_notification_permission
                            )
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
