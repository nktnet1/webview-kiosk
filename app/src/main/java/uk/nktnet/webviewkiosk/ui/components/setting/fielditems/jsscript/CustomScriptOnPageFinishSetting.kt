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
fun CustomScriptOnPageFinishSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }

    TextSettingFieldItem(
        label = stringResource(id = R.string.js_scripts_custom_script_on_page_finish_title),
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
