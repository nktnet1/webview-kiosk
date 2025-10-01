package uk.nktnet.webviewkiosk.ui.components.setting.fielditems.jsscript

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.ui.components.setting.fields.TextSettingFieldItem

@Composable
fun CustomScriptOnFinishSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }

    TextSettingFieldItem(
        label = "Custom Script (Page Finish)",
        infoText = """
            JavaScript to run after the page has fully loaded.
            Useful for DOM updates, styling, or injecting behavior.
            
            Errors in this script may still affect page functionality.
        """.trimIndent(),
        placeholder = "e.g.\ndocument.body.style.color = 'blue';",
        initialValue = userSettings.customScriptOnFinish,
        isMultiline = true,
        onSave = { userSettings.customScriptOnFinish = it }
    )
}
