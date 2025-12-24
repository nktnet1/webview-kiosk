package com.nktnet.webview_kiosk.ui.components.setting.fielditems.device

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.nktnet.webview_kiosk.R
import com.nktnet.webview_kiosk.config.UserSettings
import com.nktnet.webview_kiosk.config.UserSettingsKeys
import com.nktnet.webview_kiosk.ui.components.setting.fields.BooleanSettingFieldItem

@Composable
fun BlockVolumeKeysSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }
    val settingKey = UserSettingsKeys.Device.BLOCK_VOLUME_KEYS

    BooleanSettingFieldItem(
        label = stringResource(id = R.string.device_block_volume_keys_title),
        infoText = """
            Prevent users from changing the device volume using hardware keys while in the kiosk app.
        """.trimIndent(),
        initialValue = userSettings.blockVolumeKeys,
        settingKey = settingKey,
        restricted = userSettings.isRestricted(settingKey),
        onSave = { userSettings.blockVolumeKeys = it }
    )
}
