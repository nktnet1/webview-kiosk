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
fun LockOnLaunchSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }
    val settingKey = UserSettingsKeys.WebLifecycle.LOCK_ON_LAUNCH

    BooleanSettingFieldItem(
        label = stringResource(id = R.string.web_lifecycle_lock_on_launch_title),
        infoText = """
            When enabled, the app will immediately enter locked/pinned mode on startup,
            preventing exit until unpinned.

            On some devices, you may still be prompted with a confirmation screen.
        """.trimIndent(),
        initialValue = userSettings.lockOnLaunch,
        settingKey = settingKey,
        restricted = userSettings.isRestricted(settingKey),
        onSave = { userSettings.lockOnLaunch = it }
    )
}
