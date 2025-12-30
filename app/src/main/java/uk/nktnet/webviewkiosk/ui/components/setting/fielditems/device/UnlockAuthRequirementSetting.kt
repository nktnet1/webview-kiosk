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

@Composable
fun UnlockAuthRequirementSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }
    val settingKey = UserSettingsKeys.Device.UNLOCK_AUTH_REQUIREMENT

    DropdownSettingFieldItem(
        label = stringResource(R.string.device_unlock_auth_requirement_title),
        infoText = """
            This setting only applies to in-app unlock methods, e.g. when unlocking
            using Kiosk Control Panel or Custom Unlock Shortcut.

            Available options are:

              - DEFAULT: require authentication for lock task mode (device owner,
            fully-managed/company-owned devices), off for screen pinning (user-owned devices)

              - OFF: unlock without requiring authentication

              - REQUIRE: Prompt for biometrics or device credentials when unlocking

            For user-owned devices without lock task mode, the device will auto-lock when
            unpinning, which is outside the control of ${stringResource(R.string.app_name)}.
        """.trimIndent(),
        options = UnlockAuthRequirementOption.entries,
        initialValue = userSettings.unlockAuthRequirement,
        settingKey = settingKey,
        restricted = userSettings.isRestricted(settingKey),
        onSave = { userSettings.unlockAuthRequirement = it },
        itemText = { it.label },
    )
}
