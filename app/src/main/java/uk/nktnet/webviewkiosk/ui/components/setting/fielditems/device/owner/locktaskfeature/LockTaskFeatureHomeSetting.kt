package uk.nktnet.webviewkiosk.ui.components.setting.fielditems.device.owner.locktaskfeature

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.UserSettingsKeys
import uk.nktnet.webviewkiosk.ui.components.setting.fields.BooleanSettingFieldItem

@Composable
fun LockTaskFeatureHomeSetting() {
    val context = LocalContext.current
    val userSettings = UserSettings(context)

    BooleanSettingFieldItem(
        label = "Show Home Button",
        infoText = """
            Shows the Home button.

            Enable for custom launchers - tapping an enabled Home button has no
            action unless you allowlist the default Android launcher.
        """.trimIndent(),
        initialValue = userSettings.lockTaskFeatureHome,
        restricted = userSettings.isRestricted(UserSettingsKeys.Device.Owner.LockTaskFeature.HOME),
        onSave = { userSettings.lockTaskFeatureHome = it },
    )
}
