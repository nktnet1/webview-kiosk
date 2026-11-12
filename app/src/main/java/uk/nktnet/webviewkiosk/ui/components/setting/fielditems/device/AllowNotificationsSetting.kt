package uk.nktnet.webviewkiosk.ui.components.setting.fielditems.device

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import uk.nktnet.webviewkiosk.R
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.UserSettingsKeys
import uk.nktnet.webviewkiosk.ui.components.setting.NotificationPermissionButton
import uk.nktnet.webviewkiosk.ui.components.setting.fields.BooleanSettingFieldItem
import uk.nktnet.webviewkiosk.ui.components.setting.rememberNotificationPermissionControl
import uk.nktnet.webviewkiosk.ui.screens.settingDefinition

@Composable
fun AllowNotificationsSetting() {
    val context = LocalContext.current
    val userSettings = UserSettings(context)
    val settingKey = UserSettingsKeys.Device.ALLOW_NOTIFICATIONS
    val notificationPermission = rememberNotificationPermissionControl()

    BooleanSettingFieldItem(
        infoText =
            stringResource(
                settingDefinition(settingKey).descriptionRes,
                stringResource(R.string.app_name),
            ),
        initialValue = userSettings.allowNotifications,
        settingKey = settingKey,
        restricted = userSettings.isRestricted(settingKey),
        onSave = { userSettings.allowNotifications = it },
        itemText = { v ->
            val statusText =
                when {
                    notificationPermission.available -> ""
                    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ->
                        context.getString(
                            R.string.ui_notifications_disabled_in_android_settings
                        )
                    else -> context.getString(R.string.ui_no_permission)
                }
            if (v) {
                context.getString(R.string.ui_true_value, statusText)
            } else {
                context.getString(R.string.ui_false_value, statusText)
            }
        },
        extraContent = {
            NotificationPermissionButton(notificationPermission)
        },
    )
}
