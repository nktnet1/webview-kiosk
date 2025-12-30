package uk.nktnet.webviewkiosk.ui.components.setting.fielditems.jsscript

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import uk.nktnet.webviewkiosk.R
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.UserSettingsKeys
import uk.nktnet.webviewkiosk.ui.components.setting.fields.TextSettingFieldItem

@Composable
fun CustomScriptOnPageStartSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }
    val settingKey = UserSettingsKeys.JsScripts.CUSTOM_SCRIPT_ON_PAGE_START

    TextSettingFieldItem(
        label = stringResource(R.string.js_scripts_custom_script_on_page_start_title),
        infoText = """
            JavaScript to run immediately when the page starts loading.
            You can use this for early DOM manipulation or overriding functions.

            Your code content will be wrapped as follows to prevent
            polluting the global scope and avoid conflicts with
            other scripts:
                (function() {
                    // <YOUR CODE>
                })()
            """.trimIndent(),
        placeholder = "e.g. document.body.style.backgroundColor = 'green';",
        initialValue = userSettings.customScriptOnPageStart,
        settingKey = settingKey,
        restricted = userSettings.isRestricted(settingKey),
        isMultiline = true,
        onSave = { userSettings.customScriptOnPageStart = it }
    )
}
