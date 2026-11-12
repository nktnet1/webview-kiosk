package uk.nktnet.webviewkiosk.ui.components.setting.fielditems.unifiedpush

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import uk.nktnet.webviewkiosk.R
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.UserSettingsKeys
import uk.nktnet.webviewkiosk.ui.components.setting.fields.TextSettingFieldItem

@Composable
fun UnifiedPushMessageForDistributorSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }
    val settingKey = UserSettingsKeys.UnifiedPush.MESSAGE_FOR_DISTRIBUTOR
    val restricted = userSettings.isRestricted(settingKey)

    TextSettingFieldItem(
        placeholder =
            stringResource(
                R.string.ui_e_g_registering_value,
                stringResource(R.string.app_name),
            ),
        initialValue = userSettings.unifiedPushMessageForDistributor,
        settingKey = settingKey,
        restricted = restricted,
        isMultiline = false,
        onSave = {
            userSettings.unifiedPushMessageForDistributor = it
        },
    )
}
