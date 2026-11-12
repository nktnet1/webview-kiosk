package uk.nktnet.webviewkiosk.ui.components.setting.fielditems.webengine

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.UserSettingsKeys
import uk.nktnet.webviewkiosk.ui.components.setting.fields.BooleanSettingFieldItem

@Composable
fun BuiltInZoomControlsSetting() {
    val context = LocalContext.current
    val userSettings = UserSettings(context)
    val settingKey = UserSettingsKeys.WebEngine.BUILT_IN_ZOOM_CONTROLS

    BooleanSettingFieldItem(
        initialValue = userSettings.builtInZoomControls,
        settingKey = settingKey,
        restricted = userSettings.isRestricted(settingKey),
        onSave = { userSettings.builtInZoomControls = it },
    )
}
