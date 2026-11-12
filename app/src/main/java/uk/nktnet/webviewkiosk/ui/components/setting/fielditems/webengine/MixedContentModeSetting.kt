package uk.nktnet.webviewkiosk.ui.components.setting.fielditems.webengine

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.UserSettingsKeys
import uk.nktnet.webviewkiosk.config.option.MixedContentModeOption
import uk.nktnet.webviewkiosk.ui.components.setting.fields.DropdownSettingFieldItem
import uk.nktnet.webviewkiosk.ui.resources.displayLabelRes

@Composable
fun MixedContentModeSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }
    val settingKey = UserSettingsKeys.WebEngine.MIXED_CONTENT_MODE

    DropdownSettingFieldItem(
        options = MixedContentModeOption.entries,
        initialValue = userSettings.mixedContentMode,
        settingKey = settingKey,
        restricted = userSettings.isRestricted(settingKey),
        onSave = { userSettings.mixedContentMode = it },
        itemText = { stringResource(it.displayLabelRes) },
    )
}
