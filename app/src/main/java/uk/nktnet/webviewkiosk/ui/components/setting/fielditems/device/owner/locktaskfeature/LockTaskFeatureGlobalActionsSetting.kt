package com.nktnet.webview_kiosk.ui.components.setting.fielditems.device.owner.locktaskfeature

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.nktnet.webview_kiosk.R
import com.nktnet.webview_kiosk.config.UserSettings
import com.nktnet.webview_kiosk.config.UserSettingsKeys
import com.nktnet.webview_kiosk.ui.components.setting.fields.BooleanSettingFieldItem

@Composable
fun LockTaskFeatureGlobalActionsSetting() {
    val context = LocalContext.current
    val userSettings = UserSettings(context)
    val settingKey = UserSettingsKeys.Device.Owner.LockTaskFeature.GLOBAL_ACTIONS

    BooleanSettingFieldItem(
        label = stringResource(id = R.string.device_owner_lock_task_feature_global_actions_title),
        infoText = """
            Enables the global actions dialog that shows when long-pressing the power button.

            This is the only feature that's enabled when setLockTaskFeatures() hasn't been called.
            A user typically can't power off the device if you disable this dialog.
        """.trimIndent(),
        initialValue = userSettings.lockTaskFeatureGlobalActions,
        settingKey = settingKey,
        restricted = userSettings.isRestricted(settingKey),
        onSave = { userSettings.lockTaskFeatureGlobalActions = it },
    )
}
