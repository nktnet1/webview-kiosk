package com.nktnet.webview_kiosk.ui.components.setting.fielditems.appearance

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.nktnet.webview_kiosk.config.UserSettings
import com.nktnet.webview_kiosk.config.UserSettingsKeys
import com.nktnet.webview_kiosk.config.option.FloatingToolbarModeOption
import com.nktnet.webview_kiosk.ui.components.setting.fields.DropdownSettingFieldItem

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
        itemText = { it.label },
    )
}
