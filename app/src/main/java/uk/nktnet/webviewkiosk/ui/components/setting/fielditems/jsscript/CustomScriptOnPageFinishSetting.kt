package com.nktnet.webview_kiosk.ui.components.setting.fielditems.jsscript

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.nktnet.webview_kiosk.config.UserSettings
import com.nktnet.webview_kiosk.config.UserSettingsKeys
import com.nktnet.webview_kiosk.ui.components.setting.fields.TextSettingFieldItem

@Composable
fun CustomScriptOnPageFinishSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }

    TextSettingFieldItem(
        label = "On Page Finish (custom)",
        infoText = """
            JavaScript to run after the page has fully loaded.
            Useful for DOM updates, styling, or injecting behavior.

            Your code content will be wrapped as follows to prevent
            polluting the global scope and avoid conflicts with
            other scripts:
                (function() {
                    // <YOUR CODE>
                })()
            """.trimIndent(),
        placeholder = "e.g. document.body.style.backgroundColor = 'green';",
        initialValue = userSettings.customScriptOnPageFinish,
        restricted = userSettings.isRestricted(UserSettingsKeys.JsScripts.CUSTOM_SCRIPT_ON_PAGE_FINISH),
        isMultiline = true,
        onSave = { userSettings.customScriptOnPageFinish = it }
    )
}
