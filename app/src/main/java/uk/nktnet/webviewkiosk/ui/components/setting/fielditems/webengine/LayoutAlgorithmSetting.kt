package com.nktnet.webview_kiosk.ui.components.setting.fielditems.webengine

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.nktnet.webview_kiosk.config.UserSettings
import com.nktnet.webview_kiosk.config.UserSettingsKeys
import com.nktnet.webview_kiosk.config.option.LayoutAlgorithmOption
import com.nktnet.webview_kiosk.ui.components.setting.fields.DropdownSettingFieldItem

@Composable
fun LayoutAlgorithmSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }

    DropdownSettingFieldItem(
        label = "Layout Algorithm",
        infoText = """
            - NORMAL: no rendering changes

            - SINGLE_COLUMN: all content in one column the width of the view

            - NARROW_COLUMNS: columns no wider than screen (pre-KitKat)

            - TEXT_AUTOSIZING: boosts font size heuristically (API 19+)
        """.trimIndent(),
        options = LayoutAlgorithmOption.entries,
        initialValue = userSettings.layoutAlgorithm,
        restricted = userSettings.isRestricted(UserSettingsKeys.WebEngine.LAYOUT_ALGORITHM),
        onSave = { userSettings.layoutAlgorithm = it },
        itemText = {
            when (it) {
                LayoutAlgorithmOption.NORMAL -> "Normal"
                LayoutAlgorithmOption.SINGLE_COLUMN -> "Single Column"
                LayoutAlgorithmOption.NARROW_COLUMNS -> "Narrow Columns"
                LayoutAlgorithmOption.TEXT_AUTOSIZING -> "Text Autosizing"
            }
        }
    )
}
