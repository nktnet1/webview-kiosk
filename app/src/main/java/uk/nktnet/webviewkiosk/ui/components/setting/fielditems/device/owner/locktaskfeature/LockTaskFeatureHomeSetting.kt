package com.nktnet.webview_kiosk.ui.components.setting.fielditems.device.owner.locktaskfeature

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.nktnet.webview_kiosk.R
import com.nktnet.webview_kiosk.config.UserSettings
import com.nktnet.webview_kiosk.config.UserSettingsKeys
import com.nktnet.webview_kiosk.ui.components.setting.fields.BooleanSettingFieldItem

@Composable
fun LockTaskFeatureHomeSetting() {
    val context = LocalContext.current
    val userSettings = UserSettings(context)
    val settingKey = UserSettingsKeys.Device.Owner.LockTaskFeature.HOME

    BooleanSettingFieldItem(
        label = stringResource(id = R.string.device_owner_lock_task_feature_home_title),
        infoText = """
            Shows the Home button.

            Enable for custom launchers - tapping an enabled Home button has no
            action unless you allowlist the default Android launcher.
        """.trimIndent(),
        initialValue = userSettings.lockTaskFeatureHome,
        settingKey = settingKey,
        restricted = userSettings.isRestricted(settingKey),
        onSave = { userSettings.lockTaskFeatureHome = it },
    )
}
