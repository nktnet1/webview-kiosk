package com.nktnet.webview_kiosk.ui.components.setting.fielditems.device.owner.locktaskfeature

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.nktnet.webview_kiosk.R
import com.nktnet.webview_kiosk.config.UserSettings
import com.nktnet.webview_kiosk.config.UserSettingsKeys
import com.nktnet.webview_kiosk.ui.components.setting.fields.BooleanSettingFieldItem

@Composable
fun LockTaskFeatureKeyguardSetting() {
    val context = LocalContext.current
    val userSettings = UserSettings(context)
    val settingKey = UserSettingsKeys.Device.Owner.LockTaskFeature.KEYGUARD

    BooleanSettingFieldItem(
        label = stringResource(id = R.string.device_owner_lock_task_feature_keyguard_title),
        infoText = """
            Enables any lock screen that might be set on the device.
            Typically not suitable for devices with public users such as kiosks or digital signage.
        """.trimIndent(),
        initialValue = userSettings.lockTaskFeatureKeyguard,
        settingKey = settingKey,
        restricted = userSettings.isRestricted(settingKey),
        onSave = { userSettings.lockTaskFeatureKeyguard = it },
    )
}
