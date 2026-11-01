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
fun UnifiedPushStoreEndpointCredentialsSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }
    val settingKey = UserSettingsKeys.UnifiedPush.STORE_ENDPOINT_CREDENTIALS

    BooleanSettingFieldItem(
        label =
            stringResource(R.string.unifiedpush_store_endpoint_credentials_title),
        infoText =
            stringResource(
                R.string.setting_info_unified_push_store_endpoint_credentials,
                stringResource(R.string.app_name),
            ),
        initialValue = userSettings.unifiedPushStoreEndpointCredentials,
        settingKey = settingKey,
        restricted = userSettings.isRestricted(settingKey),
        onSave = {
            userSettings.unifiedPushStoreEndpointCredentials = it
        },
    )
}
