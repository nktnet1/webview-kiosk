package uk.nktnet.webviewkiosk.ui.components.setting.fielditems.device.owner.locktaskfeature

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import uk.nktnet.webviewkiosk.R
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.UserSettingsKeys
import uk.nktnet.webviewkiosk.ui.components.setting.fields.BooleanSettingFieldItem

@Composable
fun LockTaskFeatureHomeSetting() {
    val context = LocalContext.current
    val userSettings = UserSettings(context)

    BooleanSettingFieldItem(
        label = stringResource(id = R.string.device_owner_lock_task_feature_home_title),
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
