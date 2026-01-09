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
fun UnifiedPushEnabledSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }
    val settingKey = UserSettingsKeys.UnifiedPush.ENABLED

    BooleanSettingFieldItem(
        label = stringResource(R.string.unifiedpush_enabled_title),
        infoText = """
            When enabled, ${stringResource(R.string.app_name)} will connect to your
            configured UnifiedPush distributor.
        """.trimIndent(),
        initialValue = userSettings.unifiedPushEnabled,
        settingKey = settingKey,
        restricted = userSettings.isRestricted(settingKey),
        onSave = { isEnabled ->
            val isChanged = isEnabled != userSettings.unifiedPushEnabled
            if (isChanged) {
                userSettings.unifiedPushEnabled = isEnabled
                // TODO: connect to distributor
            }
        }
    )
}
