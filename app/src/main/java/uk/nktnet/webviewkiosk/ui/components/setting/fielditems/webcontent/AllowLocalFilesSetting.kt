package com.nktnet.webview_kiosk.ui.components.setting.fielditems.webcontent

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.nktnet.webview_kiosk.config.UserSettings
import com.nktnet.webview_kiosk.config.UserSettingsKeys
import com.nktnet.webview_kiosk.ui.components.setting.fields.BooleanSettingFieldItem

@Composable
fun AllowLocalFilesSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }

    BooleanSettingFieldItem(
        label = "Allow Local Files",
        infoText = """
            Set to true to allow the user to load a local HTML file into the WebView.

            This will be accessible using the 3-dot icon on the right of the address bar.
        """.trimIndent(),
        initialValue = userSettings.allowLocalFiles,
        restricted = userSettings.isRestricted(UserSettingsKeys.WebContent.ALLOW_LOCAL_FILES),
        onSave = { userSettings.allowLocalFiles = it }
    )
}
