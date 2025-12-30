package uk.nktnet.webviewkiosk.ui.components.setting.fielditems.webengine

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import uk.nktnet.webviewkiosk.R
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.UserSettingsKeys
import uk.nktnet.webviewkiosk.config.option.LayoutAlgorithmOption
import uk.nktnet.webviewkiosk.ui.components.setting.fields.DropdownSettingFieldItem

@Composable
fun LayoutAlgorithmSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }
    val settingKey = UserSettingsKeys.WebEngine.LAYOUT_ALGORITHM

    DropdownSettingFieldItem(
        label = stringResource(R.string.web_engine_layout_algorithm_title),
        infoText = """
            - NORMAL: no rendering changes

            - SINGLE_COLUMN: all content in one column the width of the view

            - NARROW_COLUMNS: columns no wider than screen (pre-KitKat)

            - TEXT_AUTOSIZING: boosts font size heuristically (API 19+)
        """.trimIndent(),
        options = LayoutAlgorithmOption.entries,
        initialValue = userSettings.layoutAlgorithm,
        settingKey = settingKey,
        restricted = userSettings.isRestricted(settingKey),
        onSave = { userSettings.layoutAlgorithm = it },
        itemText = { it.label }
    )
}
