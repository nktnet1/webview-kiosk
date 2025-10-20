package uk.nktnet.webviewkiosk.ui.components.setting.fielditems.device

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.UserSettingsKeys
import uk.nktnet.webviewkiosk.config.option.BackButtonHoldActionOption
import uk.nktnet.webviewkiosk.ui.components.setting.fields.DropdownSettingFieldItem

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
        itemText = {
            when (it) {
                BackButtonHoldActionOption.OPEN_KIOSK_CONTROL_PANEL -> "Open Kiosk Control Panel"
                BackButtonHoldActionOption.GO_HOME -> "Go Home"
                BackButtonHoldActionOption.DISABLED -> "Disabled"
            }
        }
    )
}
