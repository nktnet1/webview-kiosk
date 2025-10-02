package uk.nktnet.webviewkiosk.ui.components.setting.fielditems.weblifecycle

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.ui.components.setting.fields.BooleanSettingFieldItem

@Composable
fun ResetOnLaunchSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }

    BooleanSettingFieldItem(
        label = "Reset on App Launch",
        infoText = """
             When enabled, the app will always start fresh at the Home URL when
             closed and re-opened, as opposed to the last visited URL.
             
             The navigation history will also be cleared.
        """.trimIndent(),
        initialValue = userSettings.resetOnLaunch,
        onSave = { userSettings.resetOnLaunch = it }
    )
}
