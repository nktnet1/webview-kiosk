package uk.nktnet.webviewkiosk.ui.components.setting.fielditems.device

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.UserSettingsKeys
import uk.nktnet.webviewkiosk.ui.components.setting.fields.BooleanSettingFieldItem
import uk.nktnet.webviewkiosk.utils.applyPreventScreenCapture

@Composable
fun PreventScreenCaptureSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }

    BooleanSettingFieldItem(
        label = "Prevent Screen Capture",
        infoText = """
            Prevent screenshots, screen recording, and content previews in Recent Apps."
        """.trimIndent(),
        initialValue = userSettings.preventScreenCapture,
        restricted = userSettings.isRestricted(UserSettingsKeys.Device.PREVENT_SCREEN_CAPTURE),
        onSave = {
            userSettings.preventScreenCapture = it
            applyPreventScreenCapture(context)
        }
    )
}
