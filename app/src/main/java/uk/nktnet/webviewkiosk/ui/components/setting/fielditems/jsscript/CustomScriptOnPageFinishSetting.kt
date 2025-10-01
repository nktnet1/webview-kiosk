package uk.nktnet.webviewkiosk.ui.components.setting.fielditems.jsscript

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.ui.components.setting.fields.TextSettingFieldItem

@Composable
fun CustomScriptOnPageFinishSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }

    TextSettingFieldItem(
        label = "On Page Finish (custom)",
        infoText = """
            JavaScript to run after the page has fully loaded.
            Useful for DOM updates, styling, or injecting behavior.
            
            Your code content will be wrapped in:
                (function() {
                    <YOUR CODE>
                })()        """.trimIndent(),
        placeholder = "e.g. document.body.style.backgroundColor = 'green';",
        initialValue = userSettings.customScriptOnPageFinish,
        isMultiline = true,
        onSave = { userSettings.customScriptOnPageFinish = it }
    )
}
