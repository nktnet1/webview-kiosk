package uk.nktnet.webviewkiosk.ui.components.setting.fielditems.device

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.UserSettingsKeys
import uk.nktnet.webviewkiosk.ui.components.setting.fields.BooleanSettingFieldItem
import uk.nktnet.webviewkiosk.utils.applyBlockScreenCapture

@Composable
fun BlockScreenCaptureSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }

    BooleanSettingFieldItem(
        label = "Block Screen Capture",
        infoText = """
            Prevent screenshots, screen recording and content previews in Recent Apps.
            This is done by setting the FLAG_SECURE window flag.
        """.trimIndent(),
        initialValue = userSettings.blockScreenCapture,
        restricted = userSettings.isRestricted(UserSettingsKeys.Device.BLOCK_SCREEN_CAPTURE),
        onSave = {
            userSettings.blockScreenCapture = it
            applyBlockScreenCapture(context, it)
        }
    )
}
