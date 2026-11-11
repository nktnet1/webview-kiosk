package uk.nktnet.webviewkiosk.ui.components.setting.fielditems.webengine

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import uk.nktnet.webviewkiosk.R
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.UserSettingsKeys
import uk.nktnet.webviewkiosk.ui.components.setting.fields.BooleanSettingFieldItem

@Composable
fun BackgroundMediaPlaybackSetting() {
    val context = LocalContext.current
    val userSettings = remember(context) { UserSettings(context) }
    val settingKey = UserSettingsKeys.WebEngine.BACKGROUND_MEDIA_PLAYBACK
    val notificationsAvailable = rememberMediaNotificationsAvailable(context)

    BooleanSettingFieldItem(
        label = stringResource(R.string.web_engine_background_media_playback_title),
        infoText = stringResource(R.string.setting_info_background_media_playback),
        initialValue = userSettings.backgroundMediaPlayback,
        settingKey = settingKey,
        restricted = userSettings.isRestricted(settingKey),
        onSave = { userSettings.backgroundMediaPlayback = it },
        extraContentForValue = { enabled ->
            if (
                shouldShowMediaNotificationWarning(enabled, notificationsAvailable)
            ) {
                Text(
                    text =
                        stringResource(
                            R.string
                                .ui_background_media_notification_permission_warning
                        ),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
    )
}

/** Recheck after the user returns from Android's app notification settings. */
@Composable
private fun rememberMediaNotificationsAvailable(context: Context): Boolean {
    val lifecycleOwner = LocalLifecycleOwner.current
    var available by
        remember(context) {
            mutableStateOf(hasMediaNotificationPermission(context))
        }
    DisposableEffect(context, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                available = hasMediaNotificationPermission(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    return available
}

internal fun hasMediaNotificationPermission(context: Context): Boolean =
    NotificationManagerCompat.from(context).areNotificationsEnabled() &&
        (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED)

internal fun shouldShowMediaNotificationWarning(
    playbackEnabled: Boolean,
    notificationsAvailable: Boolean,
): Boolean = playbackEnabled && !notificationsAvailable
