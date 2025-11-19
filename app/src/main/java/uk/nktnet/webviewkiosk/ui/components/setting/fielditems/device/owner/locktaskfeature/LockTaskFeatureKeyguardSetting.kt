package uk.nktnet.webviewkiosk.ui.components.setting.fielditems.device.owner.locktaskfeature

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.UserSettingsKeys
import uk.nktnet.webviewkiosk.ui.components.setting.fields.BooleanSettingFieldItem

@Composable
fun LockTaskFeatureKeyguardSetting() {
    val context = LocalContext.current
    val userSettings = UserSettings(context)

    BooleanSettingFieldItem(
        label = "Enable Keyguard",
        infoText = """
            Enables any lock screen that might be set on the device. 
            Typically not suitable for devices with public users such as kiosks or digital signage.
        """.trimIndent(),
        initialValue = userSettings.lockTaskFeatureKeyguard,
        restricted = userSettings.isRestricted(UserSettingsKeys.Device.Owner.LockTaskFeature.KEYGUARD),
        onSave = { userSettings.lockTaskFeatureKeyguard = it },
    )
}
