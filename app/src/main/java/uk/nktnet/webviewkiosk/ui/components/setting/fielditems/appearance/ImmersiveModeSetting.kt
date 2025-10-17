package com.nktnet.webview_kiosk.ui.components.setting.fielditems.appearance

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.nktnet.webview_kiosk.config.UserSettings
import com.nktnet.webview_kiosk.config.UserSettingsKeys
import com.nktnet.webview_kiosk.config.option.ImmersiveModeOption
import com.nktnet.webview_kiosk.ui.components.setting.fields.DropdownSettingFieldItem

@Composable
fun ImmersiveModeSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }

    DropdownSettingFieldItem(
        label = "Immersive Mode",
        infoText = """
            In immersive mode, your system bars (status and navigation) are hidden.

            You can temporarily reveal the system bars with gestures such as swiping
            from the edge of the screen where the bar is hidden from.

            Note: immersive mode is enabled automatically when entering fullscreen
            (for example, when watching a video), irrespective of this setting.
        """.trimIndent(),
        options = ImmersiveModeOption.entries,
        restricted = userSettings.isRestricted(UserSettingsKeys.Appearance.IMMERSIVE_MODE),
        initialValue = userSettings.immersiveMode,
        onSave = { userSettings.immersiveMode = it },
        itemText = {
            when (it) {
                ImmersiveModeOption.ALWAYS_ON -> "Always on"
                ImmersiveModeOption.ALWAYS_OFF -> "Always off"
                ImmersiveModeOption.ONLY_WHEN_LOCKED -> "Only when locked"
            }
        }
    )
}
