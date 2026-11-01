package uk.nktnet.webviewkiosk.ui.components.setting.fielditems.device

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import uk.nktnet.webviewkiosk.R
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.UserSettingsKeys
import uk.nktnet.webviewkiosk.config.option.UnlockAuthRequirementOption
import uk.nktnet.webviewkiosk.ui.components.setting.fields.DropdownSettingFieldItem
import uk.nktnet.webviewkiosk.ui.resources.displayLabelRes

@Composable
fun UnlockAuthRequirementSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }
    val settingKey = UserSettingsKeys.Device.UNLOCK_AUTH_REQUIREMENT

    DropdownSettingFieldItem(
        label = stringResource(R.string.device_unlock_auth_requirement_title),
        infoText =
            stringResource(
                R.string.setting_info_unlock_auth_requirement,
                stringResource(R.string.app_name),
            ),
        options = UnlockAuthRequirementOption.entries,
        initialValue = userSettings.unlockAuthRequirement,
        settingKey = settingKey,
        restricted = userSettings.isRestricted(settingKey),
        onSave = { userSettings.unlockAuthRequirement = it },
        itemText = { stringResource(it.displayLabelRes) },
    )
}
