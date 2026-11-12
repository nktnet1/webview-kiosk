package uk.nktnet.webviewkiosk.ui.components.setting.fielditems.weblifecycle

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.UserSettingsKeys
import uk.nktnet.webviewkiosk.config.option.ResetActionOption
import uk.nktnet.webviewkiosk.ui.components.setting.fields.EnumListSettingFieldItem
import uk.nktnet.webviewkiosk.ui.resources.displayLabelRes

@Composable
fun ResetActionsSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }
    val settingKey = UserSettingsKeys.WebLifecycle.RESET_ACTIONS

    EnumListSettingFieldItem(
        entries = ResetActionOption.entries,
        getLabel = { stringResource(it.displayLabelRes) },
        getDefault = ResetActionOption::defaults,
        initialValue = userSettings.resetActions,
        settingKey = settingKey,
        restricted = userSettings.isRestricted(settingKey),
        onSave = { userSettings.resetActions = it },
    )
}
