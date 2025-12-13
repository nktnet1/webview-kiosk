package uk.nktnet.webviewkiosk.ui.components.setting.fielditems.device.owner.locktaskfeature

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import uk.nktnet.webviewkiosk.R
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.UserSettingsKeys
import uk.nktnet.webviewkiosk.ui.components.setting.fields.BooleanSettingFieldItem

@Composable
fun LockTaskFeatureOverviewSetting() {
    val context = LocalContext.current
    val userSettings = UserSettings(context)

    BooleanSettingFieldItem(
        label = stringResource(id = R.string.device_owner_lock_task_feature_overview_title),
        infoText = """
            Shows the Overview button (tapping this button opens the Recents screen).

            If you enable this button, you must also enable the Home button.
        """.trimIndent(),
        initialValue = userSettings.lockTaskFeatureOverview,
        restricted = userSettings.isRestricted(UserSettingsKeys.Device.Owner.LockTaskFeature.OVERVIEW),
        onSave = { userSettings.lockTaskFeatureOverview = it },
    )
}
