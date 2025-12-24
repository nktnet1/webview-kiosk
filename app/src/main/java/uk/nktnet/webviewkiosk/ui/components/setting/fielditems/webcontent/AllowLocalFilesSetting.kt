package com.nktnet.webview_kiosk.ui.components.setting.fielditems.webcontent

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.nktnet.webview_kiosk.R
import com.nktnet.webview_kiosk.config.UserSettings
import com.nktnet.webview_kiosk.config.UserSettingsKeys
import com.nktnet.webview_kiosk.ui.components.setting.fields.BooleanSettingFieldItem

@Composable
fun AllowLocalFilesSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }
    val settingKey = UserSettingsKeys.WebContent.ALLOW_LOCAL_FILES

    BooleanSettingFieldItem(
        label = stringResource(id = R.string.web_content_allow_local_files_title),
        infoText = """
            Set to true to allow the user to load a local HTML file into the WebView.

            This will be accessible using the 3-dot icon on the right of the address bar.
        """.trimIndent(),
        initialValue = userSettings.allowLocalFiles,
        settingKey = settingKey,
        restricted = userSettings.isRestricted(settingKey),
        onSave = { userSettings.allowLocalFiles = it }
    )
}
