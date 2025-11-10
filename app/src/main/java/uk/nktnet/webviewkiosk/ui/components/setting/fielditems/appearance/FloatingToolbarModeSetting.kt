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

            When set to "Hidden", the "Settings" button will be visible from the
            Kiosk Control Panel.
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
