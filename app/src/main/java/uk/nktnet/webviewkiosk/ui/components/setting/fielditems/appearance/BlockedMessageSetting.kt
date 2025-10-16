package uk.nktnet.webviewkiosk.ui.components.setting.fielditems.appearance

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.UserSettingsKeys
import uk.nktnet.webviewkiosk.ui.components.setting.fields.TextSettingFieldItem

@Composable
fun BlockedMessageSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }

    TextSettingFieldItem(
        label = "Blocked Message",
        infoText = "Custom message shown on blocked pages.",
        placeholder = "e.g. This site is blocked by <Company Name>",
        restricted = userSettings.isRestricted(UserSettingsKeys.Appearance.BLOCKED_MESSAGE),
        initialValue = userSettings.blockedMessage,
        isMultiline = true,
        onSave = { userSettings.blockedMessage = it }
    )
}
