package uk.nktnet.webviewkiosk.ui.components.setting.fielditems.appearance

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import uk.nktnet.webviewkiosk.R
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.UserSettingsKeys
import uk.nktnet.webviewkiosk.config.option.FloatingToolbarModeOption
import uk.nktnet.webviewkiosk.ui.components.setting.fields.DropdownSettingFieldItem

@Composable
fun FloatingToolbarModeSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }

    DropdownSettingFieldItem(
        label = stringResource(id = R.string.appearance_floating_toolbar_mode_title),
        infoText = """
            Control the visibility and behaviour of the floating toolbar menu button.

            When set to "Hidden", the "Settings" button will be visible from the
            Kiosk Control Panel.
        """.trimIndent(),
        options = FloatingToolbarModeOption.entries,
        restricted = userSettings.isRestricted(UserSettingsKeys.Appearance.FLOATING_TOOLBAR_MODE),
        initialValue = userSettings.floatingToolbarMode,
        onSave = { userSettings.floatingToolbarMode = it },
        itemText = { it.label },
    )
}
