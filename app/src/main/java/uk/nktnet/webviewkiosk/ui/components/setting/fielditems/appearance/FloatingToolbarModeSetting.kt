package uk.nktnet.webviewkiosk.ui.components.setting.fielditems.appearance

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.UserSettingsKeys
import uk.nktnet.webviewkiosk.config.option.FloatingToolbarModeOption
import uk.nktnet.webviewkiosk.ui.components.setting.fields.DropdownSettingFieldItem

@Composable
fun FloatingToolbarModeSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }

    DropdownSettingFieldItem(
        label = "Floating Toolbar Mode",
        infoText = """
            Control the visibility and behaviour of the floating toolbar menu button.

            When set to "Hidden", the "Settings" button will be available from the
            Kiosk Control Panel.

            ---

            If both of the following are true:

              1. [Web Browsing -> Kiosk Control Panel Region] is disabled
              2. [Device -> Back Button Hold Action] is not set to "Open Kiosk Control Panel"

            Then the [Web Browsing -> Kiosk Control Panel Region] cannot be disabled and
            will default to TOP_LEFT.
        """.trimIndent(),
        options = FloatingToolbarModeOption.entries,
        restricted = userSettings.isRestricted(UserSettingsKeys.Appearance.FLOATING_TOOLBAR_MODE),
        initialValue = userSettings.floatingToolbarMode,
        onSave = { userSettings.floatingToolbarMode = it },
        itemText = {
            when (it) {
                FloatingToolbarModeOption.HIDDEN -> "Hidden"
                FloatingToolbarModeOption.HIDDEN_WHEN_LOCKED -> "Hidden when locked"
                FloatingToolbarModeOption.SHOWN -> "Shown"
            }
        }
    )
}
