package uk.nktnet.webviewkiosk.ui.components.setting.fielditems.unifiedpush

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import uk.nktnet.webviewkiosk.R
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.UserSettingsKeys
import uk.nktnet.webviewkiosk.ui.components.setting.fields.BooleanSettingFieldItem

@Composable
fun UnifiedPushProcessUnencryptedMessagesSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }
    val settingKey = UserSettingsKeys.UnifiedPush.PROCESS_UNENCRYPTED_MESSAGES

    BooleanSettingFieldItem(
        label =
            stringResource(R.string.unifiedpush_process_unencrypted_messages_title),
        infoText =
            stringResource(
                R.string.setting_info_unified_push_process_unencrypted_messages,
                stringResource(R.string.app_name),
            ),
        initialValue = userSettings.unifiedPushProcessUnencryptedMessages,
        settingKey = settingKey,
        restricted = userSettings.isRestricted(settingKey),
        onSave = {
            userSettings.unifiedPushProcessUnencryptedMessages = it
        },
    )
}
