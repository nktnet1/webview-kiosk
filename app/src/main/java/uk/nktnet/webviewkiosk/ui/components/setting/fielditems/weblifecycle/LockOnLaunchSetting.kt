package uk.nktnet.webviewkiosk.ui.components.setting.fielditems.weblifecycle

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.ui.components.setting.fields.BooleanSettingFieldItem

@Composable
fun LockOnLaunchSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }

    BooleanSettingFieldItem(
        label = "Lock on App Launch",
        infoText = """
            When enabled, the app will immediately enter locked/pinned/kiosk mode
            on startup, preventing exit until unpinned.
        """.trimIndent(),
        initialValue = userSettings.lockOnLaunch,
        onSave = { userSettings.lockOnLaunch = it }
    )
}
