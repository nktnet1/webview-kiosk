package com.nktnet.webview_kiosk.ui.components.setting.fielditems.device

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.nktnet.webview_kiosk.R
import com.nktnet.webview_kiosk.config.UserSettings
import com.nktnet.webview_kiosk.config.UserSettingsKeys
import com.nktnet.webview_kiosk.config.option.BackButtonHoldActionOption
import com.nktnet.webview_kiosk.ui.components.setting.fields.DropdownSettingFieldItem

@Composable
fun BackButtonHoldActionSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }
    val settingKey = UserSettingsKeys.Device.BACK_BUTTON_HOLD_ACTION

    DropdownSettingFieldItem(
        label = stringResource(id = R.string.device_back_button_hold_action_title),
        infoText = """
            Customise the behaviour when the back button is held down (long pressed).
        """.trimIndent(),
        options = BackButtonHoldActionOption.entries,
        settingKey = settingKey,
        restricted = userSettings.isRestricted(settingKey),
        initialValue = userSettings.backButtonHoldAction,
        onSave = { userSettings.backButtonHoldAction = it },
        itemText = { it.label },
    )
}
