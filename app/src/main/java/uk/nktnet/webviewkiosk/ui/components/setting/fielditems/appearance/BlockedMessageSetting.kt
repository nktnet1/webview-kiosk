package com.nktnet.webview_kiosk.ui.components.setting.fielditems.appearance

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.nktnet.webview_kiosk.R
import com.nktnet.webview_kiosk.config.UserSettings
import com.nktnet.webview_kiosk.config.UserSettingsKeys
import com.nktnet.webview_kiosk.ui.components.setting.fields.TextSettingFieldItem

@Composable
fun BlockedMessageSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }
    val settingKey = UserSettingsKeys.Appearance.BLOCKED_MESSAGE

    TextSettingFieldItem(
        label = stringResource(id = R.string.appearance_blocked_message_title),
        infoText = "Custom message shown on blocked pages.",
        placeholder = "e.g. This site is blocked by <Company Name>",
        settingKey = settingKey,
        restricted = userSettings.isRestricted(settingKey),
        initialValue = userSettings.blockedMessage,
        isMultiline = true,
        onSave = { userSettings.blockedMessage = it }
    )
}
