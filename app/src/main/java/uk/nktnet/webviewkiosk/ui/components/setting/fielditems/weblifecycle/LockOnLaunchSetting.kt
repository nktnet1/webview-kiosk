package uk.nktnet.webviewkiosk.ui.components.setting.fielditems.weblifecycle

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.UserSettingsKeys
import uk.nktnet.webviewkiosk.ui.components.setting.fields.BooleanSettingFieldItem

@Composable
fun LockOnLaunchSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }

    BooleanSettingFieldItem(
        label = "Lock on Launch",
        infoText = """
            When enabled, the app will immediately enter locked/pinned mode on startup,
            preventing exit until unpinned.

            On some devices, you may still be prompted with a confirmation screen.
        """.trimIndent(),
        initialValue = userSettings.lockOnLaunch,
        restricted = userSettings.isRestricted(UserSettingsKeys.WebLifecycle.LOCK_ON_LAUNCH),
        onSave = { userSettings.lockOnLaunch = it }
    )
}
