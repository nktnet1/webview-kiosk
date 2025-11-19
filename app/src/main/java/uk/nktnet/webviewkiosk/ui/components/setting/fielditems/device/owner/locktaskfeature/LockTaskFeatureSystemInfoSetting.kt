package uk.nktnet.webviewkiosk.ui.components.setting.fielditems.device.owner.locktaskfeature

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.UserSettingsKeys
import uk.nktnet.webviewkiosk.ui.components.setting.fields.BooleanSettingFieldItem

@Composable
fun LockTaskFeatureSystemInfoSetting() {
    val context = LocalContext.current
    val userSettings = UserSettings(context)

    BooleanSettingFieldItem(
        label = "Show System Info",
        infoText = """
            Enables the status bar's system info area that contains indicators
            such as connectivity, battery, and sound/vibrate options.

            This requires Android 11 (API Level 30).
        """.trimIndent(),
        initialValue = userSettings.lockTaskFeatureSystemInfo,
        restricted = userSettings.isRestricted(UserSettingsKeys.Device.Owner.LockTaskFeature.SYSTEM_INFO),
        onSave = { userSettings.lockTaskFeatureSystemInfo = it },
    )
}
