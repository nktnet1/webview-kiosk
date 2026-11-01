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
import uk.nktnet.webviewkiosk.ui.resources.displayLabelRes

@Composable
fun LayoutAlgorithmSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }
    val settingKey = UserSettingsKeys.WebEngine.LAYOUT_ALGORITHM

    DropdownSettingFieldItem(
        label = stringResource(R.string.web_engine_layout_algorithm_title),
        infoText = stringResource(R.string.setting_info_layout_algorithm),
        options = LayoutAlgorithmOption.entries,
        initialValue = userSettings.layoutAlgorithm,
        settingKey = settingKey,
        restricted = userSettings.isRestricted(settingKey),
        onSave = { userSettings.layoutAlgorithm = it },
        itemText = { stringResource(it.displayLabelRes) },
    )
}
