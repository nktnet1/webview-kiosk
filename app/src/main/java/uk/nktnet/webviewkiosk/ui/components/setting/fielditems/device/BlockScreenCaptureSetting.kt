package com.nktnet.webview_kiosk.ui.components.setting.fielditems.device

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.nktnet.webview_kiosk.R
import com.nktnet.webview_kiosk.config.UserSettings
import com.nktnet.webview_kiosk.config.UserSettingsKeys
import com.nktnet.webview_kiosk.ui.components.setting.fields.BooleanSettingFieldItem
import com.nktnet.webview_kiosk.utils.applyBlockScreenCapture

@Composable
fun BlockScreenCaptureSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }
    val settingKey = UserSettingsKeys.Device.BLOCK_SCREEN_CAPTURE

    BooleanSettingFieldItem(
        label = stringResource(id = R.string.device_block_screen_capture_title),
        infoText = """
            Prevent screenshots, screen recording and content previews in Recent Apps.
            This is done by setting the FLAG_SECURE window flag.
        """.trimIndent(),
        initialValue = userSettings.blockScreenCapture,
        settingKey = settingKey,
        restricted = userSettings.isRestricted(settingKey),
        onSave = {
            userSettings.blockScreenCapture = it
            applyBlockScreenCapture(context, it)
        }
    )
}
