package com.nktnet.webview_kiosk.ui.components.setting.fielditems.weblifecycle

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.nktnet.webview_kiosk.R
import com.nktnet.webview_kiosk.config.UserSettings
import com.nktnet.webview_kiosk.config.UserSettingsKeys
import com.nktnet.webview_kiosk.ui.components.setting.fields.BooleanSettingFieldItem

@Composable
fun ResetOnLaunchSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }
    val settingKey = UserSettingsKeys.WebLifecycle.RESET_ON_LAUNCH

    BooleanSettingFieldItem(
        label = stringResource(id = R.string.web_lifecycle_reset_on_launch_title),
        infoText = """
             When enabled, the app will always start fresh at the Home URL when
             closed and re-opened, as opposed to the last visited URL.

             The navigation history will also be cleared.
        """.trimIndent(),
        initialValue = userSettings.resetOnLaunch,
        settingKey = settingKey,
        restricted = userSettings.isRestricted(settingKey),
        onSave = { userSettings.resetOnLaunch = it }
    )
}
