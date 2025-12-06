package com.nktnet.webview_kiosk.ui.components.setting.fielditems.device.owner.locktaskfeature

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.nktnet.webview_kiosk.config.UserSettings
import com.nktnet.webview_kiosk.config.UserSettingsKeys
import com.nktnet.webview_kiosk.ui.components.setting.fields.BooleanSettingFieldItem

@Composable
fun LockTaskFeatureGlobalActionsSetting() {
    val context = LocalContext.current
    val userSettings = UserSettings(context)

    BooleanSettingFieldItem(
        label = "Enable Global Actions",
        infoText = """
            Enables the global actions dialog that shows when long-pressing the power button.

            This is the only feature that's enabled when setLockTaskFeatures() hasn't been called.
            A user typically can't power off the device if you disable this dialog.
        """.trimIndent(),
        initialValue = userSettings.lockTaskFeatureGlobalActions,
        restricted = userSettings.isRestricted(UserSettingsKeys.Device.Owner.LockTaskFeature.GLOBAL_ACTIONS),
        onSave = { userSettings.lockTaskFeatureGlobalActions = it },
    )
}
