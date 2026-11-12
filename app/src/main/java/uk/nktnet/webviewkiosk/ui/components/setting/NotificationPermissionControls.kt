package uk.nktnet.webviewkiosk.ui.components.setting

import android.Manifest
import android.os.Build
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import uk.nktnet.webviewkiosk.R
import uk.nktnet.webviewkiosk.utils.PermissionState
import uk.nktnet.webviewkiosk.utils.openAppNotificationsSettings
import uk.nktnet.webviewkiosk.utils.rememberPermissionState

/**
 * Android notification access is independent of the Device > Allow Notifications app
 * preference.
 */
data class NotificationPermissionControlState(
    val available: Boolean,
    val runtimePermission: PermissionState,
    val requestPermission: () -> Unit,
)

@Composable
fun rememberNotificationPermissionControl(): NotificationPermissionControlState {
    val context = LocalContext.current
    val (runtimePermission, requestPermission) =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            rememberPermissionState(
                Manifest.permission.POST_NOTIFICATIONS,
                customOpenAction = ::openAppNotificationsSettings,
            )
        } else {
            PermissionState(granted = true, shouldShowRationale = false) to
                {
                    openAppNotificationsSettings(context)
                }
        }
    val notificationsEnabled =
        rememberNotificationsEnabled(runtimePermission.granted)
    return NotificationPermissionControlState(
        available = runtimePermission.granted && notificationsEnabled,
        runtimePermission = runtimePermission,
        requestPermission = requestPermission,
    )
}

/**
 * The same permission button is used by Device, background media, and MQTT settings.
 */
@Composable
fun NotificationPermissionButton(state: NotificationPermissionControlState) {
    Button(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        onClick = state.requestPermission,
    ) {
        Text(
            text =
                stringResource(
                    notificationPermissionButtonLabel(
                        Build.VERSION.SDK_INT,
                        state.runtimePermission,
                        state.available,
                    )
                ),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

/**
 * Show the permission action even when the service is off, so access can be granted
 * first.
 */
@Composable
fun ForegroundServiceNotificationPermissionContent(
    serviceEnabled: Boolean,
    @StringRes warningResId: Int,
) {
    val notificationPermission = rememberNotificationPermissionControl()
    Column(modifier = Modifier.fillMaxWidth()) {
        if (
            shouldWarnForForegroundServiceNotifications(
                serviceEnabled,
                notificationPermission.available,
            )
        ) {
            Text(
                text = stringResource(warningResId),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        NotificationPermissionButton(notificationPermission)
    }
}

internal fun shouldWarnForForegroundServiceNotifications(
    serviceEnabled: Boolean,
    notificationsAvailable: Boolean,
): Boolean = serviceEnabled && !notificationsAvailable

@StringRes
internal fun notificationPermissionButtonLabel(
    sdkInt: Int,
    runtimePermission: PermissionState,
    notificationsAvailable: Boolean,
): Int =
    when {
        notificationsAvailable -> R.string.ui_disable_in_app_info
        runtimePermission.granted -> R.string.ui_enable_in_app_info
        sdkInt >= Build.VERSION_CODES.TIRAMISU &&
            !runtimePermission.shouldShowRationale ->
            R.string.ui_request_notification_permission
        else -> R.string.ui_enable_in_app_info
    }

/** Refresh after returning from the system's app-notification settings. */
@Composable
private fun rememberNotificationsEnabled(runtimeGranted: Boolean): Boolean {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var enabled by
        remember(context, runtimeGranted) {
            mutableStateOf(
                NotificationManagerCompat.from(context).areNotificationsEnabled()
            )
        }
    DisposableEffect(context, lifecycleOwner, runtimeGranted) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                enabled =
                    NotificationManagerCompat.from(context).areNotificationsEnabled()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    return enabled
}
