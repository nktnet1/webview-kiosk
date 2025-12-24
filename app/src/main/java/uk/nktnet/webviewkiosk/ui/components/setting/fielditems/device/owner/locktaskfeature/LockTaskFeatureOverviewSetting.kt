package com.nktnet.webview_kiosk.ui.components.setting.fielditems.device.owner.locktaskfeature

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.nktnet.webview_kiosk.R
import com.nktnet.webview_kiosk.config.UserSettings
import com.nktnet.webview_kiosk.config.UserSettingsKeys
import com.nktnet.webview_kiosk.ui.components.setting.fields.BooleanSettingFieldItem

@Composable
fun LockTaskFeatureOverviewSetting() {
    val context = LocalContext.current
    val userSettings = UserSettings(context)
    val settingKey = UserSettingsKeys.Device.Owner.LockTaskFeature.OVERVIEW

    BooleanSettingFieldItem(
        label = stringResource(id = R.string.device_owner_lock_task_feature_overview_title),
        infoText = """
            Shows the Overview button (tapping this button opens the Recents screen).

            If you enable this button, you must also enable the Home button.
        """.trimIndent(),
        initialValue = userSettings.lockTaskFeatureOverview,
        settingKey = settingKey,
        restricted = userSettings.isRestricted(settingKey),
        onSave = { userSettings.lockTaskFeatureOverview = it },
    )
}
