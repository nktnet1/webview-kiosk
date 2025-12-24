package com.nktnet.webview_kiosk.ui.components.setting.fielditems.device

import android.Manifest
import android.os.Build
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
import com.nktnet.webview_kiosk.config.Constants
import com.nktnet.webview_kiosk.config.UserSettings
import com.nktnet.webview_kiosk.config.UserSettingsKeys
import com.nktnet.webview_kiosk.ui.components.setting.fields.BooleanSettingFieldItem
import com.nktnet.webview_kiosk.utils.PermissionState
import com.nktnet.webview_kiosk.utils.openAppNotificationsSettings
import com.nktnet.webview_kiosk.utils.rememberPermissionState

@Composable
fun AllowNotificationsSetting() {
    val context = LocalContext.current
    val userSettings = UserSettings(context)
    val settingKey = UserSettingsKeys.Device.ALLOW_NOTIFICATIONS

    val (
        permissionState,
        requestPermission
    ) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        rememberPermissionState(
            Manifest.permission.POST_NOTIFICATIONS,
            customOpenAction = ::openAppNotificationsSettings
        )
    } else {
        PermissionState(
            granted = false,
            shouldShowRationale = false,
        ) to {
            openAppNotificationsSettings(context)
        }
    }

    BooleanSettingFieldItem(
        label = stringResource(id = R.string.device_allow_notifications_title),
        infoText = """
            Set to true to allow ${Constants.APP_NAME} to send notifications.
            For example, this will allow the MQTT notify command to create alerts.

            You will need to grant the POST_NOTIFICATIONS android permission.

            Please note that for foreground services, e.g. when using lock task mode
            kiosk-launch or MQTT, the notification will always be created irrespective
            of this setting. You can disable notifications at the device level if you
            do not want them.
        """.trimIndent(),
        initialValue = userSettings.allowNotifications,
        settingKey = settingKey,
        restricted = userSettings.isRestricted(settingKey),
        onSave = { userSettings.allowNotifications = it },
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
                        "Request Notification Permission"
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
