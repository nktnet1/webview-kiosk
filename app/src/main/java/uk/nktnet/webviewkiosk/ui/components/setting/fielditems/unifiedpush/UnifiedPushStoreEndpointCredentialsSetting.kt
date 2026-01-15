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
        label = stringResource(R.string.unifiedpush_store_endpoint_credentials_title),
        infoText = """
            When enabled, ${stringResource(R.string.app_name)} will persist
            the following values:

            - Endpoint URL
            - Endpoint Public Key
            - Endpoint Auth Secret

            Ideally, these values should not be stored at all, and are instead
            sent directly to the application server at the point of registration.

            However, ${stringResource(R.string.app_name)} does not yet have an
            application server. These values are thus stored locally in the
            application so they can be copied. They can be optionally redacted
            afterwards through the settings UI.
            """.trimIndent(),
        initialValue = userSettings.unifiedPushStoreEndpointCredentials,
        settingKey = settingKey,
        restricted = userSettings.isRestricted(settingKey),
        onSave = {
            userSettings.unifiedPushStoreEndpointCredentials = it
        }
    )
}
