package uk.nktnet.webviewkiosk.ui.components.setting.fielditems.weblifecycle

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import uk.nktnet.webviewkiosk.R
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.UserSettingsKeys
import uk.nktnet.webviewkiosk.config.option.ResetActionOption
import uk.nktnet.webviewkiosk.ui.components.setting.fields.EnumListSettingFieldItem

@Composable
fun ResetActionsSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }
    val settingKey = UserSettingsKeys.WebLifecycle.RESET_ACTIONS

    EnumListSettingFieldItem(
        label = stringResource(R.string.web_lifecycle_reset_actions_title),
        infoText =
            """
            Choose what to clear when an inactivity reset returns to the home URL.

            Returning home always happens, even when no actions are selected.

            Cookies and website storage are shared across all websites in the app.
            Clearing them may sign users out of websites.

            Recreating the WebView also ends the current in-memory page session.
            """
                .trimIndent(),
        entries = ResetActionOption.entries,
        getLabel = { it.label },
        getDefault = ResetActionOption::defaults,
        initialValue = userSettings.resetActions,
        settingKey = settingKey,
        restricted = userSettings.isRestricted(settingKey),
        onSave = { userSettings.resetActions = it },
        reorderable = false,
    )
}
