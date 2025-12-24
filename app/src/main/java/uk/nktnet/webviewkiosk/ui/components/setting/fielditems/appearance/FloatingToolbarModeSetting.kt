package com.nktnet.webview_kiosk.ui.components.setting.fielditems.appearance

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.nktnet.webview_kiosk.R
import com.nktnet.webview_kiosk.config.UserSettings
import com.nktnet.webview_kiosk.config.UserSettingsKeys
import com.nktnet.webview_kiosk.config.option.FloatingToolbarModeOption
import com.nktnet.webview_kiosk.ui.components.setting.fields.DropdownSettingFieldItem

@Composable
fun FloatingToolbarModeSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }
    val settingKey = UserSettingsKeys.Appearance.FLOATING_TOOLBAR_MODE

    DropdownSettingFieldItem(
        label = stringResource(id = R.string.appearance_floating_toolbar_mode_title),
        infoText = """
            Control the visibility and behaviour of the floating toolbar menu button.

            When set to "Hidden", the "Settings" button will be visible from the
            Kiosk Control Panel.
        """.trimIndent(),
        options = FloatingToolbarModeOption.entries,
        settingKey = settingKey,
        restricted = userSettings.isRestricted(settingKey),
        initialValue = userSettings.floatingToolbarMode,
        onSave = { userSettings.floatingToolbarMode = it },
        itemText = { it.label },
    )
}
