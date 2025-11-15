package com.nktnet.webview_kiosk.ui.components.setting.fielditems.device

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.nktnet.webview_kiosk.config.UserSettings
import com.nktnet.webview_kiosk.config.UserSettingsKeys
import com.nktnet.webview_kiosk.config.option.BackButtonHoldActionOption
import com.nktnet.webview_kiosk.ui.components.setting.fields.DropdownSettingFieldItem

@Composable
fun BackButtonHoldActionSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }

    DropdownSettingFieldItem(
        label = "Back Button Hold Action",
        infoText = """
            Customise the behaviour when the back button is held down (long pressed).
        """.trimIndent(),
        options = BackButtonHoldActionOption.entries,
        restricted = userSettings.isRestricted(UserSettingsKeys.Device.BACK_BUTTON_HOLD_ACTION),
        initialValue = userSettings.backButtonHoldAction,
        onSave = { userSettings.backButtonHoldAction = it },
        itemText = { it.label },
    )
}
