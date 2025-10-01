package uk.nktnet.webviewkiosk.ui.components.setting.fielditems.jsscript

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.ui.components.setting.fields.TextSettingFieldItem

@Composable
fun CustomScriptOnStartSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }

    TextSettingFieldItem(
        label = "Custom Script (Page Start)",
        infoText = """
            JavaScript to run immediately when the page starts loading.
            You can use this for early DOM manipulation or overriding functions.
            
            Be careful: errors in this script can prevent page scripts from running.
        """.trimIndent(),
        placeholder = "e.g. document.body.style.backgroundColor = 'green';",
        initialValue = userSettings.customScriptOnStart,
        isMultiline = true,
        onSave = { userSettings.customScriptOnStart = it }
    )
}
