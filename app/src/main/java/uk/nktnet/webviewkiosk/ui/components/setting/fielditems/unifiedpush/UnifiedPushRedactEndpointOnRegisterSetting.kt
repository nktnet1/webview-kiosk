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
fun UnifiedPushRedactEndpointOnRegisterSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }
    val settingKey = UserSettingsKeys.UnifiedPush.REDACT_ENDPOINT_ON_REGISTER

    BooleanSettingFieldItem(
        label = stringResource(R.string.unifiedpush_redact_endpoint_on_register_title),
        infoText = """
            When enabled, ${stringResource(R.string.app_name)} will not persist
            the following values:
              - Endpoint URL
              - Endpoint Public Key
              - Endpoint Auth Secret

            Ideally, these values should be kept hidden and sent to the application
            server at the point of registration. However, ${stringResource(R.string.app_name)}
            does not yet have an application server.
            """.trimIndent(),
        initialValue = userSettings.unifiedPushRedactEndpointOnRegister,
        settingKey = settingKey,
        restricted = userSettings.isRestricted(settingKey),
        onSave = {
            userSettings.unifiedPushRedactEndpointOnRegister = it
        }
    )
}
